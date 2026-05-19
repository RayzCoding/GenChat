package com.genchat.agent;

import com.genchat.dto.AiChatSession;
import com.genchat.dto.OverAllState;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class DeepResearchAgent {
    private final AiChatSessionService sessionService;
    private final ChatModel chatModel;
    private final AgentTaskService agentTaskService;
    private final List<ToolCallback> webSearchToolCallbacks;
    private final ChatClient chatClient;
    private final int maxRounds;
    protected Long currentSessionId;
    private ChatMemory chatMemory;
    protected String agentType;
    protected Set<String> usedTools;
    protected long startTime;
    protected long firstResponseTime;
    private Disposable.Composite compositeDisposable;


    public DeepResearchAgent(AiChatSessionService sessionService,
                             ChatModel chatModel,
                             List<ToolCallback> webSearchToolCallbacks,
                             AgentTaskService agentTaskService,
                             int maxRounds) {
        this.agentType = "DeepResearchAgent";
        this.sessionService = sessionService;
        this.chatModel = chatModel;
        this.webSearchToolCallbacks = webSearchToolCallbacks;
        this.agentTaskService = agentTaskService;
        this.maxRounds = maxRounds;
        this.chatClient = ChatClient.builder(chatModel).build();
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

    public Flux<String> stream(String conversationsId, String question) {
        // check task
        if (agentTaskService.hasRunningTask(conversationsId)) {
            return Flux.error(new IllegalStateException("Agent is already running"));
        }
        // init sink
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        // register task
        var taskInfo = agentTaskService.registerTask(conversationsId, sink, agentType);
        if (Objects.isNull(taskInfo)) {
            return Flux.error(new IllegalStateException(new IllegalStateException("The conversation is currently in progress, Please try again later")));
        }
        initTimers();
        clearUsedTools();
        // init session and save question
        var overAllState = initStateAndSaveQuestion(conversationsId, question);
        var finalAnswerBuffer = new StringBuilder();
        var thinkingBuffer = new StringBuilder();
        compositeDisposable = Disposables.composite();

        // start flow: clarify requirement-> generate research topic -> execute loop
        agentTaskService.setDisposable(conversationsId, compositeDisposable);
        return null;
    }

    private OverAllState initStateAndSaveQuestion(String conversationsId, String question) {
        var overAllState = new OverAllState(conversationsId, question);
        var historyMessages = chatMemory.get(conversationsId);
        if (!CollectionUtils.isEmpty(historyMessages)) {
            historyMessages.forEach(overAllState::add);
        }
        overAllState.add(new UserMessage(question));
        var aiChatSession = sessionService.saveQuestion(
                AiChatSession.builder()
                        .question(question)
                        .sessionId(conversationsId)
                        .build()
        );
        currentSessionId = aiChatSession.getId();
        return overAllState;
    }

    protected void clearUsedTools() {
        if (usedTools != null) {
            usedTools.clear();
        }
    }

    protected void initTimers() {
        startTime = System.currentTimeMillis();
        firstResponseTime = 0;
    }
}
