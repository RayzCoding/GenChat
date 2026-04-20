package com.genchat.agent;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.dto.AiChatSession;
import com.genchat.entity.AgentState;
import com.genchat.entity.RoundMode;
import com.genchat.entity.RoundState;
import com.genchat.entity.SearchResult;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import com.genchat.service.AiPptInstService;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.genchat.common.utils.JsonUtil.getSafe;

@Slf4j
@Setter
public class PPTBuilderAgent {
    public static final String AGENT_TYPE = "pptBuilderAgent";
    private final ChatModel chatModel;
    private ChatMemory chatMemory;
    private final List<ToolCallback> tools;
    private ChatClient chatClient;
    private final String systemPrompt;
    private AiChatSessionService sessionService;
    private AgentTaskService agentTaskService;
    private int maxRounds;
    protected Long currentSessionId;
    protected String currentQuestion;
    protected String currentRecommendations;
    protected Set<String> usedTools;
    protected long firstResponseTime;
    protected long startTime;
    protected boolean enableRecommendations = true;
    private final PptIntentRecognizer recognizer;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PPTBuilderAgent(ChatModel chatModel,
                           AiChatSessionService sessionService,
                           AgentTaskService agentTaskService,
                           ToolCallback[] webSearchToolCallbacks,
                           AiPptInstService pptInstService,
                           int maxRounds) {
        this.systemPrompt = "";
        this.tools = Arrays.asList(webSearchToolCallbacks);
        this.chatModel = chatModel;
        this.agentTaskService = agentTaskService;
        this.sessionService = sessionService;
        this.maxRounds = maxRounds;
        this.usedTools = new HashSet<>();
        recognizer= new PptIntentRecognizer( chatClient, pptInstService);
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

    public Flux<String> stream(String conversationId, String question) {
        if (!Objects.isNull(conversationId) && agentTaskService.hasRunningTask(conversationId)) {
            return Flux.error(new IllegalStateException("The conversation is currently in progress, Please try again later."));
        }
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        // register conversation task
        var taskInfo = agentTaskService.registerTask(conversationId, sink, AGENT_TYPE);
        if (Objects.isNull(taskInfo)) {
            return Flux.error(new IllegalStateException("The conversation is currently in progress, Please try again later"));
        }
        // save current conversation message to database
        var aiChatSession = sessionService.saveQuestion(
                AiChatSession.builder()
                        .question(question)
                        .sessionId(conversationId)
                        .build()
        );
        currentSessionId = aiChatSession.getId();

        // Collect the final answer (in plain text) and store it in memory
        var finalAnswerBuffer = new StringBuilder();
        // Collecting the thought process
        var thinkingBuffer = new StringBuilder();
        var agentState = new AgentState();
        var intentResult = recognizer.recognize(conversationId, question);
        log.info("Intent result: {}", intentResult);

        return sink.asFlux()
                .doOnNext(chunk -> {
                    // When parsing JSON, if the type is "text", only the content should be concatenated; if the type is "thinking", then the thinking should be concatenated
                    try {
                        var json = JSON.parseObject(chunk);
                        var type = json.getString("type");
                        if ("text".equals(type)) {
                            finalAnswerBuffer.append(json.getString("content"));
                        } else if ("thinking".equals(type)) {
                            thinkingBuffer.append(json.getString("content"));
                        }
                    } catch (Exception e) {
                        // 解析失败，直接拼接
                        finalAnswerBuffer.append(chunk);
                    }
                })
                .doOnCancel(() -> {
                    agentTaskService.stopTask(conversationId);
                })
                .doFinally(signalType -> {
                    log.info("Final Answer: {}", finalAnswerBuffer);
                    log.info("Thinking process: {}", thinkingBuffer);
                    // 保存结果到会话
                    sessionService.update(currentSessionId, finalAnswerBuffer,
                            thinkingBuffer, agentState, firstResponseTime,
                            getTotalResponseTime(), getUsedToolsString(),
                            currentRecommendations, AGENT_TYPE);
                    // 流结束时移除任务
                    agentTaskService.stopTask(conversationId);
                });
    }

    private void scheduleRound(List<Message> messages,
                               Sinks.Many<String> sink,
                               AtomicInteger roundCounter,
                               AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer,
                               String conversationId,
                               AgentState agentState,
                               StringBuilder thinkingBuffer) {
        roundCounter.incrementAndGet();
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
                .doOnError(error -> {
                    if (!hasSentFinalResult.get()) {
                        hasSentFinalResult.set(true);
                        sink.tryEmitError(error);
                    }
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
        // non tool
        if (roundState.mode != RoundMode.TOOL_CALL) {
            String referenceJson = "";
            String toolsStr = getUsedToolsString();
            String finalText = roundState.textBuffer.toString();
            // output reference link
            if (!agentState.searchResults.isEmpty()) {
                String reference = JSON.toJSONString(agentState.searchResults);
                referenceJson = AgentResponse.reference(reference);
                sink.tryEmitNext(referenceJson);
            }
            // output recommen question
            if (enableRecommendations) {
                String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                if (recommendations != null) {
                    // Save for database storage
                    currentRecommendations = recommendations;
                    String recommendJson = AgentResponse.recommend(recommendations);
                    sink.tryEmitNext(recommendJson);
                }
            }

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
            forceFinalStream(messages, sink, hasSentFinalResult, roundState, conversationId, agentState);
            return;
        }
        executeToolCalls(sink, roundState.toolCalls,
                messages, hasSentFinalResult,
                roundState,
                agentState,
                () -> {
                    if (!hasSentFinalResult.get()) {
                        scheduleRound(messages, sink, roundCounter,
                                hasSentFinalResult, finalAnswerBuffer
                                , conversationId, agentState, thinkingBuffer);
                    }
                });
    }

    private void executeToolCalls(Sinks.Many<String> sink,
                                  List<AssistantMessage.ToolCall> toolCalls,
                                  List<Message> messages,
                                  AtomicBoolean hasSentFinalResult,
                                  RoundState roundState,
                                  AgentState agentState,
                                  Runnable onComplete) {
        var completedCount = new AtomicInteger(0);
        var totalToolCalls = toolCalls.size();

        for (var toolCall : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                if (hasSentFinalResult.get()) {
                    completeToolCall(completedCount, totalToolCalls, onComplete);
                }
                var toolName = toolCall.name();
                var argsJson = toolCall.arguments();

                var toolCallback = findTool(toolName);
                if (Objects.isNull(toolCallback)) {
                    addErrorToolResponse(messages, toolCall, "Not Found Tool:" + toolName);
                    completeToolCall(completedCount, totalToolCalls, onComplete);
                    return;
                }
                if (toolName.contains("search")) {
                    var args = JSON.parseObject(argsJson);
                    var query = (String) args.get("query");
                    // send thinking message
                    var queryThink = StringUtils.hasLength(query) ? "🔍 Searching for information: " + query + "\n" : "🔍 Searching for related information\n";
                    sink.tryEmitNext(AgentResponse.thinking(queryThink));
                }

                try {
                    var result = toolCallback.call(argsJson);
                    var tr = new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolName, result.toString());
                    messages.add(ToolResponseMessage.builder()
                            .responses(List.of(tr))
                            .build());

                    // Tools used for recording
                    recordUsedTool(toolName);

                    // Analysis of tavily search results
                    if (toolName.contains("tavily")) {
                        parseSearchResult(result.toString(), agentState);
                    }

                } catch (Exception ex) {
                    addErrorToolResponse(messages, toolCall, "Tool execution failed：" + ex.getMessage());
                } finally {
                    completeToolCall(completedCount, totalToolCalls, onComplete);
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

    private void completeToolCall(AtomicInteger completedCount, int total, Runnable onComplete) {
        int current = completedCount.incrementAndGet();
        if (current >= total) {
            onComplete.run();
        }
    }

    private void forceFinalStream(List<Message> messages,
                                  Sinks.Many<String> sink,
                                  AtomicBoolean hasSentFinalResult,
                                  RoundState roundState,
                                  String conversationId,
                                  AgentState agentState) {
        var newMessages = new ArrayList<Message>();
        newMessages.add(new SystemMessage(ReactAgentPrompts.getWebSearchPrompt()));
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
                .doOnNext(chunk -> {
                    if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                        return;
                    }
                    var text = chunk.getResult()
                            .getOutput()
                            .getText();
                    if (StringUtils.hasLength(text) && !hasSentFinalResult.get()) {
                        sink.tryEmitNext(AgentResponse.text(text));
                        finalTextBuffer.append(text);
                    }
                })
                .doOnComplete(() -> {
                    var referenceJson = "";
                    var finalText = finalTextBuffer.toString();
                    // output reference link
                    if (!agentState.searchResults.isEmpty()) {
                        var reference = JSON.toJSONString(agentState.searchResults);
                        referenceJson = AgentResponse.text(reference);
                        sink.tryEmitNext(referenceJson);
                    }
                    // output recommend question
                    if (enableRecommendations) {
                        var recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                        if (recommendations != null) {
                            currentRecommendations = recommendations;
                            String recommendJson = AgentResponse.recommend(recommendations);
                            sink.tryEmitNext(recommendJson);
                        }
                    }

                    hasSentFinalResult.set(true);
                    sink.tryEmitComplete();
                })
                .doOnError(error -> {
                    hasSentFinalResult.set(true);
                    sink.tryEmitError(error);
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
            // add system prompt
            messages.add(new SystemMessage(ReactAgentPrompts.getRecommendPrompt()));
            // add history message
            loadChatHistory(conversationId, messages, true, true);
            // add current conversation message
            messages.add(new UserMessage("Current conversation: "));
            messages.add(new UserMessage(currentQuestion));
            if (StringUtils.hasLength(currentAnswer)) {
                messages.add(new AssistantMessage(currentAnswer));
            }

            // add style introduction message
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
                    var jsonStr = JSON.toJSONString(recommendations);
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

        // 新的 toolcall
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

    protected long getTotalResponseTime() {
        if (startTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }
}
