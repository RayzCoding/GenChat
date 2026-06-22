package com.genchat.agent.core;

import com.genchat.common.utils.JacksonJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.ToolRecord;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.dto.AiChatSession;
import com.genchat.entity.AgentState;
import com.genchat.entity.RoundMode;
import com.genchat.entity.RoundState;
import com.genchat.entity.SearchResult;
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
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.ParameterizedTypeReference;
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

import static com.genchat.common.utils.JsonUtil.getSafe;

/**
 * Shared ReAct loop engine for conversation agents with persistent memory.
 */
@Slf4j
@Setter
public abstract class AbstractReactAgent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected final ChatModel chatModel;
    protected final AiChatSessionService sessionService;
    protected final AgentTaskService agentTaskService;
    protected final List<ToolCallback> tools;
    protected ChatClient chatClient;
    protected ChatMemory chatMemory;
    protected final String systemPrompt = "";
    protected int maxRounds;
    protected int maxRetries;
    protected Long currentSessionId;
    protected String currentQuestion;
    protected String currentRecommendations;
    protected long firstResponseTime;
    protected long startTime;
    protected boolean enableRecommendations = true;
    protected final List<ToolRecord> toolRecords = Collections.synchronizedList(new ArrayList<>());

    protected AbstractReactAgent(ChatModel chatModel,
                                 AiChatSessionService sessionService,
                                 AgentTaskService agentTaskService,
                                 List<ToolCallback> tools) {
        this.chatModel = chatModel;
        this.sessionService = sessionService;
        this.agentTaskService = agentTaskService;
        this.tools = tools;
        initChatClient();
    }

    protected abstract String getAgentType();

    protected abstract String getPrimarySystemPrompt();

    protected abstract AiChatSession buildSessionForSave(ReactStreamRequest request);

    protected void appendExtraUserMessages(List<Message> messages, ReactStreamRequest request) {
    }

    protected AgentState createAgentState() {
        return new AgentState();
    }

    protected void emitToolThinking(String toolName, String argsJson, Sinks.Many<String> sink) {
    }

    protected void handleToolResult(String toolName, String result, AgentState agentState) {
        if (toolName.contains("tavily") && agentState != null) {
            parseSearchResult(result, agentState);
        }
    }

    protected void onNonToolFinish(String finalText,
                                   Sinks.Many<String> sink,
                                   String conversationId,
                                   AgentState agentState) {
        if (agentState != null && !agentState.searchResults.isEmpty()) {
            sink.tryEmitNext(AgentStreamEvent.Reference.of(JacksonJson.toJson(agentState.searchResults)).toJSON());
        }
        if (enableRecommendations) {
            String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
            if (recommendations != null) {
                currentRecommendations = recommendations;
                sink.tryEmitNext(AgentStreamEvent.Recommend.of(recommendations).toJSON());
            }
        }
    }

    protected void onForceFinalComplete(String finalText,
                                        Sinks.Many<String> sink,
                                        String conversationId,
                                        AgentState agentState) {
        onNonToolFinish(finalText, sink, conversationId, agentState);
    }

    protected String getForceFinalSystemPrompt() {
        return getPrimarySystemPrompt();
    }

    protected void processForceFinalChunk(ChatResponse chunk,
                                          Sinks.Many<String> sink,
                                          RoundState roundState,
                                          AtomicBoolean hasSentFinalResult,
                                          StringBuilder finalTextBuffer) {
        if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return;
        }
        var text = chunk.getResult().getOutput().getText();
        if (StringUtils.hasLength(text) && !hasSentFinalResult.get()) {
            sink.tryEmitNext(new AgentStreamEvent.Text(text).toJSON());
            finalTextBuffer.append(text);
        }
    }

    protected Flux<String> streamInternal(ReactStreamRequest request) {
        String conversationId = request.conversationId();
        String question = request.question();

        if (!Objects.isNull(conversationId) && agentTaskService.hasRunningTask(conversationId)) {
            return Flux.error(new IllegalStateException("The conversation is currently in progress, Please try again later."));
        }
        initTimers();
        toolRecords.clear();
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        var taskInfo = agentTaskService.registerTask(conversationId, sink, getAgentType());
        if (Objects.isNull(taskInfo)) {
            return Flux.error(new IllegalStateException("The conversation is currently in progress, Please try again later"));
        }

        var aiChatSession = sessionService.saveQuestion(buildSessionForSave(request));
        currentSessionId = aiChatSession.getId();

        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        messages.add(new SystemMessage(getPrimarySystemPrompt()));
        if (StringUtils.hasLength(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        loadChatHistory(conversationId, messages, true, true);

        messages.add(new UserMessage("<question>" + question + "</question>"));
        appendExtraUserMessages(messages, request);
        currentQuestion = question;

        var roundCounter = new AtomicInteger(0);
        var hasSentFinalResult = new AtomicBoolean(false);
        var finalAnswerBuffer = new StringBuilder();
        var thinkingBuffer = new StringBuilder();
        var agentState = createAgentState();

        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer,
                conversationId, agentState, thinkingBuffer);

        return sink.asFlux()
                .doOnNext(chunk -> {
                    recordFirstResponse();
                    try {
                        var json = JacksonJson.parseTreeLenient(chunk);
                        if (json != null) {
                            String type = json.path("type").asText(null);
                            if ("text".equals(type)) {
                                finalAnswerBuffer.append(json.path("content").asText(""));
                            } else if ("thinking".equals(type)) {
                                thinkingBuffer.append(json.path("content").asText(""));
                            }
                        } else {
                            finalAnswerBuffer.append(chunk);
                        }
                    } catch (Exception e) {
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
                    sessionService.update(currentSessionId, finalAnswerBuffer,
                            thinkingBuffer, agentState, firstResponseTime,
                            getTotalResponseTime(), JacksonJson.toJson(toolRecords),
                            currentRecommendations, getAgentType(), null);
                    agentTaskService.stopTask(conversationId);
                });
    }

    public void initPersistentChatMemory(String conversationId) {
        int maxMessages = 30;
        var historyMessages = sessionService.queryRecentBySessionId(conversationId, maxMessages);
        var memory = MessageWindowChatMemory.builder().maxMessages(maxMessages).build();
        if (!CollectionUtils.isEmpty(historyMessages)) {
            historyMessages.forEach(message -> {
                var userQuestion = message.getQuestion();
                var systemAnswer = message.getAnswer();
                if (!ObjectUtils.isEmpty(userQuestion)) {
                    memory.add(conversationId, new UserMessage(userQuestion));
                }
                if (!ObjectUtils.isEmpty(systemAnswer)) {
                    memory.add(conversationId, new AssistantMessage(systemAnswer));
                }
            });
            log.info("Loading history messages, conversationId: {}, recordCount: {}", conversationId, historyMessages.size());
        }
        this.chatMemory = memory;
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

    private void scheduleRound(List<Message> messages,
                               Sinks.Many<String> sink,
                               AtomicInteger roundCounter,
                               AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer,
                               String conversationId,
                               AgentState agentState,
                               StringBuilder thinkingBuffer) {
        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer,
                conversationId, agentState, thinkingBuffer, 0);
    }

    private void scheduleRound(List<Message> messages,
                               Sinks.Many<String> sink,
                               AtomicInteger roundCounter,
                               AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer,
                               String conversationId,
                               AgentState agentState,
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
                        conversationId, agentState, thinkingBuffer))
                .onErrorResume(error -> {
                    if (retryAttempt < maxRounds) {
                        log.warn("LLM stream error (attempt {}/{}), retrying in {}ms: {}",
                                retryAttempt + 1, maxRetries, 1000, error.getMessage());
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED", "LLM call failed, and the attempt is being reattempted ("
                                + (retryAttempt + 1) + "/" + maxRetries + ")", error.getMessage()).toJSON());
                        Schedulers.boundedElastic().schedule(() -> scheduleRound(messages, sink, roundCounter,
                                hasSentFinalResult, finalAnswerBuffer,
                                conversationId, agentState, thinkingBuffer, retryAttempt + 1));
                    } else {
                        log.error("LLM stream error, retries exhausted {}ms: {}", maxRetries, error.getMessage());
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED",
                                "LLM call failed, and the attempt is being reattempted (" + maxRetries + ")",
                                error.getMessage()).toJSON());
                        sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
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
                             AgentState agentState,
                             StringBuilder thinkingBuffer) {
        if (roundState.mode != RoundMode.TOOL_CALL) {
            String finalText = roundState.textBuffer.toString();
            onNonToolFinish(finalText, sink, conversationId, agentState);
            sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
            sink.tryEmitComplete();
            hasSentFinalResult.set(true);
            return;
        }

        var assistantMessage = AssistantMessage.builder()
                .toolCalls(roundState.toolCalls)
                .build();
        messages.add(assistantMessage);
        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            forceFinalStream(messages, sink, hasSentFinalResult, roundState, conversationId, agentState);
            return;
        }
        executeToolCalls(sink, roundState.toolCalls,
                messages, hasSentFinalResult,
                agentState,
                () -> {
                    if (!hasSentFinalResult.get()) {
                        scheduleRound(messages, sink, roundCounter,
                                hasSentFinalResult, finalAnswerBuffer,
                                conversationId, agentState, thinkingBuffer);
                    }
                });
    }

    private void executeToolCalls(Sinks.Many<String> sink,
                                  List<AssistantMessage.ToolCall> toolCalls,
                                  List<Message> messages,
                                  AtomicBoolean hasSentFinalResult,
                                  AgentState agentState,
                                  Runnable onComplete) {
        var completedCount = new AtomicInteger(0);
        var totalToolCalls = toolCalls.size();
        Map<String, ToolResponseMessage.ToolResponse> responseMap = new ConcurrentHashMap<>();

        for (var toolCall : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                if (hasSentFinalResult.get()) {
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }
                var toolName = toolCall.name();
                var argsJson = toolCall.arguments();
                log.info(">>> ToolStart: {} | args: {}", toolName, argsJson);
                sink.tryEmitNext(new AgentStreamEvent.ToolStart(toolName, toolCall.id(), argsJson).toJSON());

                var toolCallback = findTool(toolName);
                if (Objects.isNull(toolCallback)) {
                    String errorMsg = "Tool not found：" + toolName;
                    log.warn("<<< ToolEnd (NOT_FOUND): {}", toolName);
                    sink.tryEmitNext(new AgentStreamEvent.ToolEnd(toolName, toolCall.id(), errorMsg).toJSON());
                    responseMap.put(toolCall.id(), new ToolResponseMessage.ToolResponse(
                            toolCall.id(),
                            toolCall.name(),
                            "{ \"error\": \"" + "Not Found Tool:" + toolName + "\" }"
                    ));
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }

                emitToolThinking(toolName, argsJson, sink);

                try {
                    var result = toolCallback.call(argsJson);
                    toolRecords.add(new ToolRecord(toolName, toolCall.id(), argsJson, result));
                    log.info("<<< ToolEnd: {}| result: {}", toolName, result);
                    sink.tryEmitNext(new AgentStreamEvent.ToolEnd(toolName, toolCall.id(), result).toJSON());
                    responseMap.put(toolCall.id(), new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, result));
                    handleToolResult(toolName, result, agentState);
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

    private void parseSearchResult(String resultJson, AgentState state) {
        try {
            JsonNode root = MAPPER.readTree(resultJson);
            if (!root.isArray() || root.isEmpty()) {
                return;
            }
            JsonNode first = root.get(0);
            JsonNode textNode = first.get("text");
            if (textNode == null || textNode.isNull()) {
                return;
            }

            JsonNode textJson;
            if (textNode.isTextual()) {
                textJson = MAPPER.readTree(textNode.asText());
            } else {
                textJson = textNode;
            }

            JsonNode results = textJson.get("results");
            if (results == null || !results.isArray()) {
                return;
            }

            for (JsonNode item : results) {
                String url = getSafe(item, "url");
                String title = getSafe(item, "title");
                String content = getSafe(item, "content");

                if (url != null && !url.isBlank()) {
                    state.searchResults.add(new SearchResult(url, title, content));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse tavily search results: {}", e.getMessage());
        }
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
                                  List<Message> messages,
                                  Runnable onComplete) {
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
                                  String conversationId,
                                  AgentState agentState) {
        forceFinalStream(messages, sink, hasSentFinalResult, roundState, conversationId, agentState, 0);
    }

    private void forceFinalStream(List<Message> messages,
                                  Sinks.Many<String> sink,
                                  AtomicBoolean hasSentFinalResult,
                                  RoundState roundState,
                                  String conversationId,
                                  AgentState agentState,
                                  int retryAttempt) {
        var newMessages = new ArrayList<Message>();
        newMessages.add(new SystemMessage(getForceFinalSystemPrompt()));
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
                .doOnNext(chunk -> processForceFinalChunk(chunk, sink, roundState, hasSentFinalResult, finalTextBuffer))
                .doOnComplete(() -> {
                    var finalText = finalTextBuffer.toString();
                    onForceFinalComplete(finalText, sink, conversationId, agentState);
                    sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
                    hasSentFinalResult.set(true);
                    sink.tryEmitComplete();
                })
                .onErrorResume(error -> {
                    if (retryAttempt < maxRounds) {
                        log.warn("forceFinal stream error (attempt {}/{}), retrying in {}ms: {}",
                                retryAttempt + 1, maxRetries, 1000, error.getMessage());
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED", "LLM call failed, and the attempt is being reattempted ("
                                + (retryAttempt + 1) + "/" + maxRetries + ")", error.getMessage()).toJSON());
                        Schedulers.boundedElastic().schedule(() -> forceFinalStream(messages, sink,
                                hasSentFinalResult, roundState, conversationId, agentState, retryAttempt + 1));
                    } else {
                        log.error("forceFinal stream error, retries exhausted {}ms: {}", maxRetries, error.getMessage());
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED",
                                "LLM call failed, and the attempt is being reattempted (" + maxRetries + ")",
                                error.getMessage()).toJSON());
                        sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
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

    private String generateRecommendations(String conversationId, String currentQuestion, String currentAnswer) {
        if (!enableRecommendations) {
            return null;
        }
        try {
            var messages = new ArrayList<Message>();
            messages.add(new SystemMessage(ReactAgentPrompts.getRecommendPrompt()));
            loadChatHistory(conversationId, messages, true, true);
            messages.add(new UserMessage("Current conversation: "));
            messages.add(new UserMessage(currentQuestion));
            if (StringUtils.hasLength(currentAnswer)) {
                messages.add(new AssistantMessage(currentAnswer));
            }

            var converter = new BeanOutputConverter<List<String>>(new ParameterizedTypeReference<>() {
            });
            messages.add(new UserMessage("Please generate 3 recommended questions based on the above dialogue. The output format is as follows：\n" + converter.getFormat()));
            var response = ChatClient.builder(chatModel).build()
                    .prompt()
                    .messages(messages)
                    .call()
                    .content();
            if (response != null && !response.isEmpty()) {
                List<String> recommendations = converter.convert(response);
                if (!CollectionUtils.isEmpty(recommendations)) {
                    var jsonStr = JacksonJson.toJson(recommendations);
                    log.info("The recommendation question has been successfully generated: {}", jsonStr);
                    return jsonStr;
                }
            }
            log.warn("Failed to generate recommendation questions, response format is invalid: {}", response);
            return null;
        } catch (Exception e) {
            log.error("Anomaly in generating recommendation questions", e);
            return null;
        }
    }

    protected void processChunk(ChatResponse chunk, Sinks.Many<String> sink, RoundState roundState) {
        if (Objects.isNull(chunk)) {
            return;
        }
        var gen = chunk.getResult();
        var text = gen.getOutput().getText();
        var toolCalls = gen.getOutput().getToolCalls();
        if (!ObjectUtils.isEmpty(toolCalls)) {
            roundState.mode = RoundMode.TOOL_CALL;
            toolCalls.forEach(toolCall -> mergeToolCall(roundState, toolCall));
            return;
        }
        if (StringUtils.hasLength(text)) {
            sink.tryEmitNext(new AgentStreamEvent.Text(text).toJSON());
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
        state.toolCalls.add(incoming);
    }

    protected String getHistoryLabel() {
        return "Conversation history：";
    }

    private void loadChatHistory(String conversationId, List<Message> messages, boolean skipSystem, boolean addLabel) {
        if (!ObjectUtils.isEmpty(conversationId) && !ObjectUtils.isEmpty(chatMemory)) {
            var history = chatMemory.get(conversationId);
            if (!ObjectUtils.isEmpty(history)) {
                if (addLabel) {
                    messages.add(new UserMessage(getHistoryLabel()));
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
