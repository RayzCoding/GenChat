package com.genchat.agent.core;

import com.genchat.application.agent.PersistentChatAgent;
import com.genchat.application.stream.AgentStreamLifecycle;
import com.genchat.application.stream.PersistentChatMemoryLoader;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.ToolRecord;
import com.genchat.common.utils.JacksonJson;
import com.genchat.dto.AiChatSession;
import com.genchat.agent.model.AgentState;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared ReAct loop engine for conversation agents with persistent memory.
 */
@Slf4j
@Setter
public abstract class AbstractReactAgent implements PersistentChatAgent {

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
            ReactToolSupport.parseTavilySearchResult(result, agentState);
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

    protected Flux<String> streamInternal(ReactStreamRequest request) {
        String conversationId = request.conversationId();
        String question = request.question();

        if (AgentStreamLifecycle.isConversationBusy(agentTaskService, conversationId)) {
            return AgentStreamLifecycle.conversationBusyError();
        }
        initTimers();
        toolRecords.clear();

        var started = AgentStreamLifecycle.startStream(agentTaskService, conversationId, getAgentType());
        if (started == null) {
            return AgentStreamLifecycle.conversationBusyError();
        }
        Sinks.Many<String> sink = started.sink();

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
        var buffers = AgentStreamLifecycle.StreamBuffers.create();
        var agentState = createAgentState();

        createRoundScheduler().scheduleRound(messages, sink, roundCounter, hasSentFinalResult, buffers.finalAnswer(),
                conversationId, agentState, buffers.thinking());

        return AgentStreamLifecycle.attach(
                started.flux(),
                conversationId,
                agentTaskService,
                buffers,
                this::recordFirstResponse,
                () -> hasSentFinalResult.set(true),
                () -> {
                    AgentStreamLifecycle.logStreamBuffers(buffers);
                    sessionService.update(currentSessionId, buffers.finalAnswer(),
                            buffers.thinking(), agentState, firstResponseTime,
                            getTotalResponseTime(), JacksonJson.toJson(toolRecords),
                            currentRecommendations, getAgentType(), null);
                });
    }

    public void initPersistentChatMemory(String conversationId, int maxMessages) {
        this.chatMemory = PersistentChatMemoryLoader.load(sessionService, conversationId, maxMessages);
    }

    private ReactRoundScheduler createRoundScheduler() {
        return new ReactRoundScheduler(
                chatClient,
                tools,
                agentTaskService,
                maxRounds,
                maxRetries,
                toolRecords,
                new ReactRoundScheduler.Callbacks() {
                    @Override
                    public void emitToolThinking(String toolName, String argsJson, Sinks.Many<String> sink) {
                        AbstractReactAgent.this.emitToolThinking(toolName, argsJson, sink);
                    }

                    @Override
                    public void handleToolResult(String toolName, String result, AgentState agentState) {
                        AbstractReactAgent.this.handleToolResult(toolName, result, agentState);
                    }

                    @Override
                    public void onNonToolFinish(String finalText, Sinks.Many<String> sink,
                                                String conversationId, AgentState agentState) {
                        AbstractReactAgent.this.onNonToolFinish(finalText, sink, conversationId, agentState);
                    }

                    @Override
                    public void onForceFinalComplete(String finalText, Sinks.Many<String> sink,
                                                     String conversationId, AgentState agentState) {
                        AbstractReactAgent.this.onForceFinalComplete(finalText, sink, conversationId, agentState);
                    }

                    @Override
                    public String getForceFinalSystemPrompt() {
                        return AbstractReactAgent.this.getForceFinalSystemPrompt();
                    }

                    @Override
                    public String getSystemPrompt() {
                        return systemPrompt;
                    }
                });
    }

    private String generateRecommendations(String conversationId, String currentQuestion, String currentAnswer) {
        if (!enableRecommendations) {
            return null;
        }
        return ReactRecommendationService.generate(
                chatModel,
                buildHistoryMessages(conversationId, true, true),
                currentQuestion,
                currentAnswer);
    }

    protected String getHistoryLabel() {
        return "Conversation history：";
    }

    private void loadChatHistory(String conversationId, List<Message> messages, boolean skipSystem, boolean addLabel) {
        var historyMessages = buildHistoryMessages(conversationId, skipSystem, addLabel);
        if (!historyMessages.isEmpty()) {
            messages.addAll(historyMessages);
        }
    }

    private List<Message> buildHistoryMessages(String conversationId, boolean skipSystem, boolean addLabel) {
        var historyMessages = new ArrayList<Message>();
        if (!ObjectUtils.isEmpty(conversationId) && !ObjectUtils.isEmpty(chatMemory)) {
            var history = chatMemory.get(conversationId);
            if (!ObjectUtils.isEmpty(history)) {
                if (addLabel) {
                    historyMessages.add(new UserMessage(getHistoryLabel()));
                }
                for (Message msg : history) {
                    if (skipSystem && msg instanceof SystemMessage) {
                        continue;
                    }
                    historyMessages.add(msg);
                }
            }
        }
        return historyMessages;
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
