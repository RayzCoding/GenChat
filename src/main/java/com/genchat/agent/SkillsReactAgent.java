package com.genchat.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.dto.AiChatSession;
import com.genchat.entity.RoundMode;
import com.genchat.entity.RoundState;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Setter
public class SkillsReactAgent {
    public static final String AGENT_TYPE = "skillsReactAgent";
    private final ChatModel chatModel;
    private ChatMemory chatMemory;
    private final List<ToolCallback> tools;
    private ChatClient chatClient;
    private final String systemPrompt;
    private AiChatSessionService sessionService;
    private AgentTaskService agentTaskService;
    private int maxRounds;
    private int maxRetries;
    protected Long currentSessionId;
    protected String currentQuestion;
    protected String currentFileId;
    protected String currentRecommendations;
    protected Set<String> usedTools;
    protected long firstResponseTime;
    protected long startTime;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SkillsReactAgent(ChatModel chatModel,
                            AiChatSessionService sessionService,
                            AgentTaskService agentTaskService,
                            ToolCallback[] webSearchToolCallbacks,
                            int maxRounds) {
        this.systemPrompt = "";
        this.tools = Arrays.asList(webSearchToolCallbacks);
        this.chatModel = chatModel;
        this.agentTaskService = agentTaskService;
        this.sessionService = sessionService;
        this.maxRounds = maxRounds;
        this.usedTools = new HashSet<>();
        initChatClient();
    }

    private void initChatClient() {
        var toolCallingChatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .internalToolExecutionEnabled(false)
                .build();
        this.chatClient = ChatClient.builder(this.chatModel)
                .defaultOptions(toolCallingChatOptions)
                .defaultToolCallbacks(tools)
                .build();
    }

    public Flux<String> stream(String conversationId, String question, String fileId) {
        if (!Objects.isNull(conversationId) && agentTaskService.hasRunningTask(conversationId)) {
            return Flux.error(new IllegalStateException("The conversation is currently in progress, Please try again later."));
        }
        initTimers();
        clearUsedTools();
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        // register conversation task
        var taskInfo = agentTaskService.registerTask(conversationId, sink, AGENT_TYPE);
        if (Objects.isNull(taskInfo)) {
            return Flux.error(new IllegalStateException("The conversation is currently in progress, Please try again later"));
        }
        this.currentFileId = fileId;
        // save current conversation message to database
        var aiChatSession = sessionService.saveQuestion(AiChatSession.builder().question(question).sessionId(conversationId).build());
        currentSessionId = aiChatSession.getId();
        // loading system prompt
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        messages.add(new SystemMessage(ReactAgentPrompts.getSkillsPrompt()));
        if (StringUtils.hasLength(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        // loading history
        boolean skipSystem = true;
        boolean addLabel = true;
        loadChatHistory(conversationId, messages, skipSystem, addLabel);

        messages.add(new UserMessage("<question>" + question + "</question>"));
        if (StringUtils.hasLength(currentFileId)) {
            messages.add(new UserMessage("<file>" + currentFileId + "</file>"));
        }
        currentQuestion = question;

        // iteration round
        var roundCounter = new AtomicInteger(0);
        // Whether to send the final result flag
        var hasSentFinalResult = new AtomicBoolean(false);
        // Collect the final answer (in plain text) and store it in memory
        var finalAnswerBuffer = new StringBuilder();
        // Collecting the thought process
        var thinkingBuffer = new StringBuilder();

        // add Round
        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer,
                conversationId, thinkingBuffer);

        return sink.asFlux()
                .doOnNext(chunk -> {
                    recordFirstResponse();
                    // When parsing JSON, if the type is "text", only the content should be concatenated; if the type is "thinking", then the thinking should be concatenated
                    try {
                        JSONObject json = JSON.parseObject(chunk);
                        String type = json.getString("type");
                        if ("text".equals(type)) {
                            finalAnswerBuffer.append(json.getString("content"));
                        } else if ("thinking".equals(type)) {
                            thinkingBuffer.append(json.getString("content"));
                        }
                    } catch (Exception e) {
                        // Parse failed, concatenate directly
                        finalAnswerBuffer.append(chunk);
                    }
                })
                .doOnCancel(() -> {
                    hasSentFinalResult.set(true);
                    agentTaskService.stopTask(conversationId);
                })
                .doFinally(signalType -> {
                    log.info("Final Answer: {}", finalAnswerBuffer);
                    log.info("Thinking process: {}", thinkingBuffer);
                    // Save result to session
                    sessionService.update(currentSessionId, finalAnswerBuffer,
                            thinkingBuffer, null, firstResponseTime,
                            getTotalResponseTime(), getUsedToolsString(),
                            currentRecommendations, AGENT_TYPE, null);
                    // Remove task when stream ends
                    agentTaskService.stopTask(conversationId);
                });
    }

    private void scheduleRound(List<Message> messages,
                               Sinks.Many<String> sink,
                               AtomicInteger roundCounter,
                               AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer,
                               String conversationId,
                               StringBuilder thinkingBuffer) {
        scheduleRound(messages, sink, roundCounter,
                hasSentFinalResult, finalAnswerBuffer,
                conversationId, thinkingBuffer, 0);
    }

    private void scheduleRound(List<Message> messages,
                               Sinks.Many<String> sink,
                               AtomicInteger roundCounter,
                               AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer,
                               String conversationId,
                               StringBuilder thinkingBuffer,
                               int retryAttempt) {
        roundCounter.incrementAndGet();
        log.info("Round Counter: {}, message size:{}", roundCounter.get(), messages.size());
        var roundState = new RoundState();

        var disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, roundState))
                .doOnComplete(() -> finishRound(messages, sink,
                        roundState, roundCounter,
                        hasSentFinalResult, finalAnswerBuffer,
                        conversationId, thinkingBuffer))
                .onErrorResume(error -> {
                    if (retryAttempt < maxRounds) {
                        log.warn("LLM stream error (attempt {}/{}), retrying in {}ms: {}",
                                retryAttempt + 1, maxRetries, 1000, error.getMessage());
                        sink.tryEmitNext(AgentResponse.error("LLM call failed, and the attempt is being reattempted ("
                                + (retryAttempt + 1) + "/" + maxRetries + ")"));
                        Schedulers.boundedElastic().schedule(() -> scheduleRound(messages, sink, roundCounter,
                                hasSentFinalResult, finalAnswerBuffer,
                                conversationId, thinkingBuffer, retryAttempt + 1));
                    } else {
                        log.error("LLM stream error, retries exhausted {}ms: {}", maxRetries, error.getMessage());
                        sink.tryEmitNext(AgentResponse.error("LLM call failed, and the attempt is being reattempted (" + maxRetries + ")"));
                        sink.tryEmitNext(AgentResponse.complete());
                        hasSentFinalResult.set(true);
                        sink.tryEmitComplete();
                    }
                    return Flux.empty();
                })
                .subscribe();

        if (conversationId != null) {
            agentTaskService.setDisposable(conversationId, disposable);
        }
    }

    private void finishRound(List<Message> messages,
                             Sinks.Many<String> sink,
                             RoundState roundState,
                             AtomicInteger roundCounter,
                             AtomicBoolean hasSentFinalResult,
                             StringBuilder finalAnswerBuffer,
                             String conversationId,
                             StringBuilder thinkingBuffer) {
        // non tool
        if (roundState.mode != RoundMode.TOOL_CALL) {
            sink.tryEmitNext(AgentResponse.complete());
            sink.tryEmitComplete();
            hasSentFinalResult.set(true);
            return;
        }
        // tool call
        var assistantMessage = AssistantMessage.builder()
                .toolCalls(roundState.toolCalls)
                .build();
        messages.add(assistantMessage);
        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            forceFinalStream(messages, sink, hasSentFinalResult, roundState, conversationId);
            return;
        }
        executeToolCalls(sink, roundState.toolCalls,
                messages, hasSentFinalResult,
                () -> {
                    if (!hasSentFinalResult.get()) {
                        scheduleRound(messages, sink, roundCounter,
                                hasSentFinalResult, finalAnswerBuffer
                                , conversationId, thinkingBuffer);
                    }
                });
    }

    private void executeToolCalls(Sinks.Many<String> sink,
                                  List<AssistantMessage.ToolCall> toolCalls,
                                  List<Message> messages,
                                  AtomicBoolean hasSentFinalResult,
                                  Runnable onComplete) {
        var completedCount = new AtomicInteger(0);
        var totalToolCalls = toolCalls.size();
        Map<String, ToolResponseMessage.ToolResponse> responseMap = new ConcurrentHashMap<>();

        for (var toolCall : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                if (hasSentFinalResult.get()) {
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                }
                var toolName = toolCall.name();
                var argsJson = toolCall.arguments();

                var toolCallback = findTool(toolName);
                if (Objects.isNull(toolCallback)) {
                    responseMap.put(toolCall.id(), new ToolResponseMessage.ToolResponse(
                            toolCall.id(),
                            toolCall.name(),
                            "{ \"error\": \"" + "Not Found Tool:" + toolName + "\" }"
                    ));
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }
                try {
                    var result = toolCallback.call(argsJson);
                    // Tools used for recording
                    recordUsedTool(toolName);
                    responseMap.put(toolCall.id(), new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, result));
                } catch (Exception ex) {
                    responseMap.put(toolCall.id(), new ToolResponseMessage.ToolResponse(
                            toolCall.id(),
                            toolCall.name(),
                            "{ \"error\": \"" + "Tool Execute failed:" + ex.getMessage() + "\" }"
                    ));
                } finally {
                    if (!hasSentFinalResult.get()) {
                        completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    }
                }
            });
        }
    }

    private void addErrorToolResponse(List<Message> messages, AssistantMessage.ToolCall toolCall, String errMsg) {
        ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                toolCall.id(),
                toolCall.name(),
                "{ \"error\": \"" + errMsg + "\" }"
        );

        messages.add(ToolResponseMessage.builder()
                .responses(List.of(tr))
                .build());
    }

    private ToolCallback findTool(String name) {
        return tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private void completeToolCall(AtomicInteger completedCount,
                                  int total,
                                  Map<String, ToolResponseMessage.ToolResponse> responseMap,
                                  List<AssistantMessage.ToolCall> toolCalls,
                                  List<Message> messages, Runnable onComplete) {
        int current = completedCount.incrementAndGet();
        if (current >= total) {
            List<ToolResponseMessage.ToolResponse> sortedResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : toolCalls) {
                ToolResponseMessage.ToolResponse response = responseMap.get(tc.id());
                if (response != null) {
                    sortedResponses.add(response);
                } else {
                    sortedResponses.add(new ToolResponseMessage.ToolResponse(
                            tc.id(), tc.name(), "{ \"error\": \"Tool response is missing\" }"));
                }
            }

            messages.add(ToolResponseMessage.builder().responses(sortedResponses).build());
            onComplete.run();
        }
    }

    private void forceFinalStream(List<Message> messages,
                                  Sinks.Many<String> sink,
                                  AtomicBoolean hasSentFinalResult,
                                  RoundState roundState,
                                  String conversationId) {
        forceFinalStream(messages, sink, hasSentFinalResult,
                roundState, conversationId, 0);
    }

    private void forceFinalStream(List<Message> messages,
                                  Sinks.Many<String> sink,
                                  AtomicBoolean hasSentFinalResult,
                                  RoundState roundState,
                                  String conversationId,
                                  int retryAttempt) {
        var newMessages = new ArrayList<Message>();
        newMessages.add(new SystemMessage(ReactAgentPrompts.getFilePrompt()));
        if (StringUtils.hasLength(systemPrompt)) {
            newMessages.add(new SystemMessage(systemPrompt));
        }

        messages.forEach(message -> {
            if (!(message instanceof SystemMessage)) {
                newMessages.add(message);
            }
        });
        newMessages.add(new UserMessage("""
                You have reached the maximum number of reasoning rounds.
                Based on the current context information,
                please provide the final answer directly.
                Do not use any further tools.
                If the information is incomplete, please summarize and explain it reasonably.
                """));

        messages.clear();
        messages.addAll(newMessages);

        var finalTextBuffer = new StringBuilder();
        var disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, roundState))
                .doOnComplete(() -> {
                    sink.tryEmitNext(AgentResponse.complete());
                    sink.tryEmitComplete();
                    hasSentFinalResult.set(true);
                })
                .onErrorResume(error -> {
                    if (retryAttempt < maxRounds) {
                        log.warn("forceFinal stream error (attempt {}/{}), retrying in {}ms: {}",
                                retryAttempt + 1, maxRetries, 1000, error.getMessage());
                        sink.tryEmitNext(AgentResponse.error("LLM call failed, and the attempt is being reattempted ("
                                + (retryAttempt + 1) + "/" + maxRetries + ")"));
                        Schedulers.boundedElastic().schedule(() -> forceFinalStream(messages, sink,
                                hasSentFinalResult, roundState, conversationId, retryAttempt + 1));
                    } else {
                        log.error("forceFinal stream error, retries exhausted {}ms: {}", maxRetries, error.getMessage());
                        sink.tryEmitNext(AgentResponse.error("LLM call failed, and the attempt is being reattempted (" + maxRetries + ")"));
                        sink.tryEmitNext(AgentResponse.complete());
                        hasSentFinalResult.set(true);
                        sink.tryEmitComplete();
                    }
                    return Flux.empty();
                })
                .subscribe();

        if (conversationId != null) {
            agentTaskService.setDisposable(conversationId, disposable);
        }
    }

    private void processChunk(ChatResponse chunk, Sinks.Many<String> sink, RoundState roundState) {
        if (Objects.isNull(chunk)) {
            return;
        }
        var gen = chunk.getResult();
        var text = gen.getOutput().getText();
        var toolCalls = gen.getOutput().getToolCalls();
        // into Tool call model
        if (!ObjectUtils.isEmpty(toolCalls)) {
            roundState.mode = RoundMode.TOOL_CALL;
            toolCalls.forEach(toolCall -> mergeToolCall(roundState, toolCall));
            return;
        }
        // cache text
        if (StringUtils.hasLength(text)) {
            sink.tryEmitNext(AgentResponse.text(text));
            roundState.textBuffer.append(text);
        }
    }

    private void mergeToolCall(RoundState state, AssistantMessage.ToolCall incoming) {
        for (int i = 0; i < state.toolCalls.size(); i++) {
            AssistantMessage.ToolCall existing = state.toolCalls.get(i);

            if (existing.id().equals(incoming.id())) {
                String mergedArgs = Objects.toString(existing.arguments(), "") + Objects.toString(incoming.arguments(), "");

                state.toolCalls.set(i,
                        new AssistantMessage.ToolCall(existing.id(), "function", existing.name(), mergedArgs)
                );
                return;
            }
        }

        // New tool call
        state.toolCalls.add(incoming);
    }

    private void loadChatHistory(String conversationId, List<Message> messages, boolean skipSystem, boolean addLabel) {
        if (!ObjectUtils.isEmpty(conversationId) && !ObjectUtils.isEmpty(chatMemory)) {
            var history = chatMemory.get(conversationId);
            if (!ObjectUtils.isEmpty(history)) {
                if (addLabel) {
                    messages.add(new UserMessage("Conversation history："));
                }
                for (Message msg : history) {
                    if (skipSystem && msg instanceof SystemMessage) {
                        continue;
                    }
                    messages.add(msg);
                }
            }

        }
    }

    public void initPersistentChatMemory(String conversationId) {
        int maxMessages = 30;
        var historyMessages = sessionService.queryRecentBySessionId(conversationId, maxMessages);
        var chatMemory = MessageWindowChatMemory.builder().maxMessages(maxMessages).build();
        if (!CollectionUtils.isEmpty(historyMessages)) {
            historyMessages.forEach(message -> {
                var userQuestion = message.getQuestion();
                var systemAnswer = message.getAnswer();
                if (!ObjectUtils.isEmpty(userQuestion)) {
                    chatMemory.add(conversationId, new UserMessage(userQuestion));
                }
                if (!ObjectUtils.isEmpty(systemAnswer)) {
                    chatMemory.add(conversationId, new AssistantMessage(systemAnswer));
                }
            });
            log.info("Loading history messages, conversationId: {}, recordCount: {}", conversationId, historyMessages.size());
        }
        this.chatMemory = chatMemory;
    }

    protected void recordUsedTool(String toolName) {
        if (usedTools != null && toolName != null) {
            usedTools.add(toolName);
        }
    }

    protected String getUsedToolsString() {
        if (usedTools == null || usedTools.isEmpty()) {
            return "";
        }
        return String.join(",", usedTools);
    }


    protected void clearUsedTools() {
        if (usedTools != null) {
            usedTools.clear();
        }
    }

    protected void recordFirstResponse() {
        if (firstResponseTime == 0 && startTime > 0) {
            firstResponseTime = System.currentTimeMillis() - startTime;
            log.debug("Record first response time: {}ms", firstResponseTime);
        }
    }

    protected long getTotalResponseTime() {
        if (startTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }

    protected void initTimers() {
        startTime = System.currentTimeMillis();
        firstResponseTime = 0;
    }
}
