package com.genchat.agent;

import com.alibaba.fastjson.JSON;
import com.genchat.application.strategy.PptStateStrategyContext;
import com.genchat.application.strategy.PptStateStrategyFactory;
import com.genchat.common.AgentStreamEvent;
import com.genchat.dto.AiChatSession;
import com.genchat.entity.AgentState;
import com.genchat.entity.PptInstStatus;
import com.genchat.service.*;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;

@Slf4j
@Setter
public class PPTBuilderAgent {
    public static final String AGENT_TYPE = "pptBuilderAgent";
    private final ChatModel chatModel;
    private ChatMemory chatMemory;
    private final List<ToolCallback> tools;
    private ChatClient chatClient;
    private AiChatSessionService sessionService;
    private AgentTaskService agentTaskService;
    protected Long currentSessionId;
    protected String currentRecommendations;
    protected long firstResponseTime;
    private final PptIntentRecognizer recognizer;
    private final AiPptInstService pptInstService;
    private final ImageGenerationService imageGenerationService;
    private final AiPptTemplateService pptTemplateService;
    private final MinioService minioService;
    private final PptPythonRenderService pptPythonRenderService;
    private PptStateStrategyContext strategyContext;


    public PPTBuilderAgent(ChatModel chatModel,
                           AiChatSessionService sessionService,
                           AgentTaskService agentTaskService,
                           ToolCallback[] webSearchToolCallbacks,
                           AiPptInstService pptInstService,
                           AiPptTemplateService pptTemplateService,
                           MinioService minioService,
                           ImageGenerationService imageGenerationService,
                           PptPythonRenderService pptPythonRenderService) {
        this.pptTemplateService = pptTemplateService;
        this.tools = Arrays.asList(webSearchToolCallbacks);
        this.chatModel = chatModel;
        this.agentTaskService = agentTaskService;
        this.sessionService = sessionService;
        this.pptInstService = pptInstService;
        this.minioService = minioService;
        this.imageGenerationService = imageGenerationService;
        this.pptPythonRenderService = pptPythonRenderService;
        recognizer = new PptIntentRecognizer(chatClient, pptInstService);
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
        try {

            var intentResult = recognizer.recognize(conversationId, question);
            log.info("Intent result: {}", intentResult);

            initStrategyContext();

            switch (intentResult.getIntent()) {
                case CREATE_PPT -> handleCreateIntent(conversationId, question, sink, thinkingBuffer);
                case MODIFY_PPT -> handleModifyIntent(conversationId, question, sink, thinkingBuffer);
                case RESUME_PPT -> handleResumeIntent(conversationId, question, sink, thinkingBuffer);
                default -> {
                    sink.tryEmitNext("If your intention is not recognized, please rephrase it.");
                    sink.tryEmitComplete();
                }
            }
        } catch (Exception e) {
            log.error("Error while recognizing intent.", e);
            sink.tryEmitError(e);
        }

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
                        // If the parse fails, it will be spliced directly
                        log.error("Error while parsing JSON.", e);
                        finalAnswerBuffer.append(chunk);
                    }
                })
                .doOnCancel(() -> {
                    agentTaskService.stopTask(conversationId);
                })
                .doFinally(signalType -> {
                    log.info("Final Answer: {}", finalAnswerBuffer);
                    log.info("Thinking process: {}", thinkingBuffer);
                    // Save result to session
                    sessionService.update(currentSessionId, finalAnswerBuffer,
                            thinkingBuffer, agentState, firstResponseTime,
                            0, null,
                            currentRecommendations, AGENT_TYPE, null);
                    // Remove task when stream ends
                    agentTaskService.stopTask(conversationId);
                });
    }

    private void initStrategyContext() {
        this.strategyContext = new PptStateStrategyContext(pptInstService,
                chatModel,
                chatMemory,
                chatClient,
                tools,
                agentTaskService,
                pptTemplateService,
                currentSessionId,
                sessionService,
                imageGenerationService,
                pptPythonRenderService,
                minioService);
    }

    private void handleResumeIntent(String conversationId,
                                    String question,
                                    Sinks.Many<String> sink,
                                    StringBuilder thinkingBuffer) {
        var latestInst = pptInstService.getLatestInst(conversationId);
        if (Objects.isNull(latestInst)) {
            log.info("No latest inst found for conversation id {}", conversationId);
            var response = "There is no generated PPT in the current session, so it cannot be modified. Please make a PPT.";
            sink.tryEmitNext(response);
            saveSession(currentSessionId, response, thinkingBuffer);
            sink.tryEmitComplete();
            return;
        }
        if (latestInst.getStatus().equals(PptInstStatus.SUCCESS.name())) {
            var response = "The current PPT has been successfully generated, if you want to modify, please explain the specific modification requirements.";
            sink.tryEmitNext(new AgentStreamEvent.Thinking(response + "\n").toJSON());
            sink.tryEmitNext(new AgentStreamEvent.Text(response).toJSON());
            sink.tryEmitComplete();
            return;
        }
        sink.tryEmitNext(new AgentStreamEvent.Thinking("Is from the state " + latestInst.getStatus() + " Proceed to perform PPT generation...\n").toJSON());
        PptStateStrategyFactory.getInstance().executeNextState(latestInst, sink, question, thinkingBuffer, strategyContext);
    }

    private void handleModifyIntent(String conversationId,
                                    String question,
                                    Sinks.Many<String> sink,
                                    StringBuilder thinkingBuffer) {
        var latestInst = pptInstService.getLatestInst(conversationId);
        if (Objects.isNull(latestInst)) {
            log.info("No latest inst found for conversation id {}", conversationId);
            var response = "There is no generated PPT in the current session, so it cannot be modified. Please make a PPT.";
            sink.tryEmitNext(new AgentStreamEvent.Text(response).toJSON());
            saveSession(currentSessionId, response, thinkingBuffer);
            sink.tryEmitComplete();
            return;
        }
        var pptSchema = latestInst.getPptSchema();
        if (Objects.isNull(pptSchema)) {
            var response = "This PPT does not have Schema data and cannot be modified.";
            sink.tryEmitNext(new AgentStreamEvent.Text(response).toJSON());
            saveSession(currentSessionId, response, thinkingBuffer);
            sink.tryEmitComplete();
            return;
        }
        sink.tryEmitNext(new AgentStreamEvent.Thinking("The PPT is being modified...\n").toJSON());
        sink.tryEmitNext(new AgentStreamEvent.Thinking("Modification requirements are being analyzed...\n").toJSON());
        sink.tryEmitNext(new AgentStreamEvent.Thinking("The content of the PPT is being modified...\n").toJSON());
        // Set modification action tags and modification requirements
        strategyContext.setModifyMode(true);
        strategyContext.setModifyQuestion(question);
        // Call SchemaStrategy directly to continue execution (it handles image generation, rendering, etc.)
        PptStateStrategyFactory.getInstance().executeSchemaStrategy(latestInst, sink, question, thinkingBuffer, strategyContext);
    }

    private void saveSession(Long currentSessionId, String response, StringBuilder thinkingBuffer) {
        if (currentSessionId == null) {
            return;
        }
        var chatSessionOptional = sessionService.queryById(currentSessionId);
        if (Objects.isNull(chatSessionOptional)) {
            return;
        }
        var aiChatSession = chatSessionOptional.get();
        aiChatSession.setAnswer(response);
        aiChatSession.setThinking(thinkingBuffer.toString());
        sessionService.updateSession(aiChatSession);
    }

    private void handleCreateIntent(String conversationId,
                                    String question,
                                    Sinks.Many<String> sink,
                                    StringBuilder thinkingBuffer) {
        sink.tryEmitNext(new AgentStreamEvent.Thinking("Start creating new PPT....").toJSON());
        var aiPptInst = pptInstService.create(conversationId, question);

        PptStateStrategyFactory.getInstance().executeNextState(aiPptInst, sink, question, thinkingBuffer, strategyContext);
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
}
