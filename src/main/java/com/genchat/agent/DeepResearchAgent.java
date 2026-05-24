package com.genchat.agent;

import com.alibaba.fastjson2.JSON;
import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.PlanExecutePrompts;
import com.genchat.common.utils.ThinkTagParser;
import com.genchat.dto.AiChatSession;
import com.genchat.dto.OverAllState;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
        var finished = new AtomicBoolean(false);

        // init session and save question
        var overAllState = initStateAndSaveQuestion(conversationsId, question);
        var finalAnswerBuffer = new StringBuilder();
        var thinkingBuffer = new StringBuilder();
        compositeDisposable = Disposables.composite();

        // start flow: clarify requirement-> generate research topic -> execute loop
        clarifyRequirement(overAllState, sink, finished,
                () -> generateResearchTopicPhase(overAllState, sink, finished, thinkingBuffer));
        agentTaskService.setDisposable(conversationsId, compositeDisposable);

        return sink.asFlux()
                .doOnNext(chunk -> {
                    recordFirstResponse();
                    parseAndAppendToBuffers(chunk, finalAnswerBuffer, thinkingBuffer);
                })
                .doOnCancel(() -> {
                    finished.set(true);
                    agentTaskService.stopTask(conversationsId);
                })
                .doFinally(signalType -> {
                    log.info("Agent has finished, conversationId: {}", conversationsId);
                    finished.set(true);
                    var totalResponseTime = System.currentTimeMillis() - startTime;
                    sessionService.update(currentSessionId, finalAnswerBuffer
                            , thinkingBuffer, null, totalResponseTime, firstResponseTime,
                            String.join(",", usedTools), null, agentType);
                    agentTaskService.stopTask(conversationsId);
                    compositeDisposable.dispose();
                });
    }

    private void parseAndAppendToBuffers(String chunk, StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer) {
        try {
            var jsonObject = JSON.parseObject(chunk);
            var type = jsonObject.get("type");
            if ("text".equals(type)) {
                finalAnswerBuffer.append(jsonObject.get("content"));
            } else if ("thinking".equals(type)) {
                thinkingBuffer.append(jsonObject.get("content"));
            }
        } catch (Exception e) {
            finalAnswerBuffer.append(chunk);
        }
    }

    private void generateResearchTopicPhase(OverAllState overAllState,
                                            Sinks.Many<String> sink,
                                            AtomicBoolean finished,
                                            StringBuilder thinkingBuffer) {
        emitThinking(sink, finished, "📝Research topics are being generated\n");
        var messages = new ArrayList<Message>();
        messages.add(new SystemMessage(PlanExecutePrompts.getCurrentTime()
                + "\n\n" + PlanExecutePrompts.RESEARCH_TOPIC_GENERATION));
        if (!CollectionUtils.isEmpty(overAllState.getMessages())) {
            messages.addAll(overAllState.getMessages());
        }
        messages.add(new UserMessage("<original_question>" + overAllState.getQuestion() + "</original_question>"));
        var topicInThinkHolder = new AtomicBoolean(false);
        var topicBuffer = new StringBuilder();

        var disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    var parse = ThinkTagParser.parse(chunk, topicInThinkHolder.get());
                    topicInThinkHolder.set(parse.inThink());
                    for (var segment : parse.segments()) {
                        emitThinking(sink, finished, segment.content());
                        if (!segment.thinking()) {
                            topicBuffer.append(segment.content());
                        }
                    }
                })
                .doOnComplete(() -> {
                    var topic = topicBuffer.toString();
                    overAllState.setRefinedResearchTopic(topic);
                    emitThinking(sink,finished,"\n✅ The research topic has been generated\n\n");
                })
                .doOnError(throwable -> {
                    log.error("Research topic generation failed", throwable);
                    if (finished.compareAndSet(false, true)) {
                        sink.tryEmitError(throwable);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        compositeDisposable.add(disposable);
    }

    private void clarifyRequirement(OverAllState overAllState,
                                    Sinks.Many<String> sink,
                                    AtomicBoolean finished,
                                    Runnable onComplete) {
        emitThinking(sink, finished, "\n🔍Your needs are being analyzed...\n");
        var messages = new ArrayList<Message>();
        messages.add(new SystemMessage(PlanExecutePrompts.getCurrentTime()
                + "\n\n" + PlanExecutePrompts.REQUIREMENT_CLARIFICATION));
        messages.addAll(overAllState.getMessages());

        var responseBuffer = new StringBuilder();
        var inThinkHolder = new AtomicBoolean(false);
        var disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    var parse = ThinkTagParser.parse(chunk, inThinkHolder.get());
                    inThinkHolder.set(parse.inThink());
                    for (var segment : parse.segments()) {
                        emitThinking(sink, finished, segment.content());
                        if (!segment.thinking()) {
                            responseBuffer.append(segment.content());
                        }
                    }
                })
                .doOnComplete(() -> handleClarificationComplete(sink, responseBuffer, finished, onComplete))
                .doOnError(throwable -> {
                    log.error("Clarify requirements error, please try again later");
                    if (finished.compareAndSet(false, true)) {
                        sink.tryEmitError(throwable);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        compositeDisposable.add(disposable);
    }

    private void handleClarificationComplete(Sinks.Many<String> sink,
                                             StringBuilder responseBuffer,
                                             AtomicBoolean finished,
                                             Runnable onComplete) {
        var response = responseBuffer.toString();
        emitThinking(sink, finished, "\n✅Requirements analysis completed\n");
        boolean needsMoreInfo = response.contains("【Additional information is needed】");
        if (needsMoreInfo) {
            String pauseMessage = "⏸【Pause for in-depth research】" + response.replace("【Additional information is needed】", "").trim();
            sink.tryEmitNext(AgentResponse.text(pauseMessage));
            if (finished.compareAndSet(false, true)) {
                sink.tryEmitComplete();
            }
            return;
        }
        emitThinking(sink, finished, "✅ Sufficient information and ready to generate a research topic\n");
        onComplete.run();
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

    protected void recordFirstResponse() {
        if (firstResponseTime == 0 && startTime > 0) {
            firstResponseTime = System.currentTimeMillis() - startTime;
            log.debug("Record the first response time: {}ms", firstResponseTime);
        }
    }

    protected void initTimers() {
        startTime = System.currentTimeMillis();
        firstResponseTime = 0;
    }

    private void emitThinking(Sinks.Many<String> sink,
                              AtomicBoolean finished,
                              String content) {

        if (finished.get()) {
            return;
        }
        sink.tryEmitNext(AgentResponse.thinking(content));
    }
}
