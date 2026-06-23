package com.genchat.agent;

import com.genchat.application.agent.PersistentChatAgent;
import com.genchat.application.stream.AgentStreamLifecycle;
import com.genchat.application.stream.PersistentChatMemoryLoader;
import com.genchat.application.strategy.PptStateStrategyContext;
import com.genchat.application.strategy.PptStrategyDependencies;
import com.genchat.common.AgentStreamEvent;
import com.genchat.dto.AiChatSession;
import com.genchat.agent.model.AgentState;
import com.genchat.entity.PptInstStatus;
import com.genchat.service.*;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;

@Slf4j
@Setter
public class PPTBuilderAgent implements PersistentChatAgent {
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
    private final PptStrategyDependencies pptStrategyDependencies;
    private PptStateStrategyContext strategyContext;


    public PPTBuilderAgent(ChatModel chatModel,
                           AiChatSessionService sessionService,
                           AgentTaskService agentTaskService,
                           ToolCallback[] webSearchToolCallbacks,
                           PptStrategyDependencies pptStrategyDependencies) {
        this.pptStrategyDependencies = pptStrategyDependencies;
        this.pptTemplateService = pptStrategyDependencies.pptTemplateService();
        this.tools = Arrays.asList(webSearchToolCallbacks);
        this.chatModel = chatModel;
        this.agentTaskService = agentTaskService;
        this.sessionService = sessionService;
        this.pptInstService = pptStrategyDependencies.pptInstService();
        this.minioService = pptStrategyDependencies.minioService();
        this.imageGenerationService = pptStrategyDependencies.imageGenerationService();
        this.pptPythonRenderService = pptStrategyDependencies.pptPythonRenderService();
        initChatClient();
        this.recognizer = new PptIntentRecognizer(chatClient, pptInstService);
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
        if (AgentStreamLifecycle.isConversationBusy(agentTaskService, conversationId)) {
            return AgentStreamLifecycle.conversationBusyError();
        }

        var started = AgentStreamLifecycle.startStream(agentTaskService, conversationId, AGENT_TYPE);
        if (started == null) {
            return AgentStreamLifecycle.conversationBusyError();
        }
        Sinks.Many<String> sink = started.sink();

        var aiChatSession = sessionService.saveQuestion(
                AiChatSession.builder()
                        .question(question)
                        .sessionId(conversationId)
                        .build()
        );
        currentSessionId = aiChatSession.getId();

        var buffers = AgentStreamLifecycle.StreamBuffers.create();
        var agentState = new AgentState();
        try {

            var intentResult = recognizer.recognize(conversationId, question);
            log.info("Intent result: {}", intentResult);

            initStrategyContext();

            switch (intentResult.getIntent()) {
                case CREATE_PPT -> handleCreateIntent(conversationId, question, sink, buffers.thinking());
                case MODIFY_PPT -> handleModifyIntent(conversationId, question, sink, buffers.thinking());
                case RESUME_PPT -> handleResumeIntent(conversationId, question, sink, buffers.thinking());
                default -> {
                    sink.tryEmitNext(new AgentStreamEvent.Text(
                            "If your intention is not recognized, please rephrase it.").toJSON());
                    sink.tryEmitComplete();
                }
            }
        } catch (Exception e) {
            log.error("Error while recognizing intent.", e);
            sink.tryEmitError(e);
        }

        return AgentStreamLifecycle.attach(
                started.flux(),
                conversationId,
                agentTaskService,
                buffers,
                null,
                null,
                () -> {
                    AgentStreamLifecycle.logStreamBuffers(buffers);
                    sessionService.update(currentSessionId, buffers.finalAnswer(),
                            buffers.thinking(), agentState, firstResponseTime,
                            0, null,
                            currentRecommendations, AGENT_TYPE, null);
                });
    }

    private void initStrategyContext() {
        this.strategyContext = PptStateStrategyContext.forSession(
                pptStrategyDependencies,
                chatMemory,
                chatClient,
                tools,
                currentSessionId);
    }

    private void handleResumeIntent(String conversationId,
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
        if (latestInst.getStatus().equals(PptInstStatus.SUCCESS.name())) {
            var response = "The current PPT has been successfully generated, if you want to modify, please explain the specific modification requirements.";
            sink.tryEmitNext(new AgentStreamEvent.Thinking(response + "\n").toJSON());
            sink.tryEmitNext(new AgentStreamEvent.Text(response).toJSON());
            sink.tryEmitComplete();
            return;
        }
        sink.tryEmitNext(new AgentStreamEvent.Thinking("Is from the state " + latestInst.getStatus() + " Proceed to perform PPT generation...\n").toJSON());
        pptStrategyDependencies.strategyFactory().executeNextState(latestInst, sink, question, thinkingBuffer, strategyContext);
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
            sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
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
        pptStrategyDependencies.strategyFactory().executeSchemaStrategy(latestInst, sink, question, thinkingBuffer, strategyContext);
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

        pptStrategyDependencies.strategyFactory().executeNextState(aiPptInst, sink, question, thinkingBuffer, strategyContext);
    }

    public void initPersistentChatMemory(String conversationId, int maxMessages) {
        this.chatMemory = PersistentChatMemoryLoader.load(sessionService, conversationId, maxMessages);
    }
}
