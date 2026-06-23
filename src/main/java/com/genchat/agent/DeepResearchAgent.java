package com.genchat.agent;

import com.genchat.application.agent.PersistentChatAgent;
import com.genchat.application.stream.AgentStreamLifecycle;
import com.genchat.application.stream.PersistentChatMemoryLoader;
import com.genchat.common.utils.JacksonJson;
import com.genchat.agent.deepresearch.DeepResearchDependencies;
import com.genchat.agent.deepresearch.DeepResearchPlanLoop;
import com.genchat.agent.deepresearch.DeepResearchPreparation;
import com.genchat.agent.deepresearch.DeepResearchRunContext;
import com.genchat.agent.deepresearch.DeepResearchStreams;
import com.genchat.common.AgentStreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class DeepResearchAgent implements PersistentChatAgent {

    private final DeepResearchDependencies deps;
    private final DeepResearchPreparation preparation;
    private final DeepResearchPlanLoop planLoop;

    private ChatMemory chatMemory;

    public void initPersistentChatMemory(String conversationId, int maxMessages) {
        this.chatMemory = PersistentChatMemoryLoader.load(deps.sessionService(), conversationId, maxMessages);
    }

    public Flux<String> stream(String conversationsId, String question) {
        if (AgentStreamLifecycle.isConversationBusy(deps.agentTaskService(), conversationsId)) {
            return AgentStreamLifecycle.conversationBusyError(() -> "Agent is already running");
        }

        var started = AgentStreamLifecycle.startStream(
                deps.agentTaskService(), conversationsId, deps.agentType());
        if (started == null) {
            return AgentStreamLifecycle.conversationBusyError();
        }
        Sinks.Many<String> sink = started.sink();

        var ctx = new DeepResearchRunContext(deps);
        DeepResearchStreams.initTimers(ctx);
        ctx.getToolRecords().clear();
        ctx.setCompositeDisposable(Disposables.composite());
        ctx.getAllReferences().clear();

        var finished = new AtomicBoolean(false);
        var overAllState = preparation.initStateAndSaveQuestion(ctx, conversationsId, question, chatMemory);
        var buffers = AgentStreamLifecycle.StreamBuffers.create();

        preparation.clarifyRequirement(ctx, overAllState, sink, finished,
                () -> preparation.generateResearchTopicPhase(ctx, overAllState, sink, finished,
                        () -> planLoop.executeLoopPhase(ctx, overAllState, sink, finished, buffers.finalAnswer())));

        deps.agentTaskService().setDisposable(conversationsId, ctx.getCompositeDisposable());

        return AgentStreamLifecycle.attach(
                started.flux(),
                conversationsId,
                deps.agentTaskService(),
                buffers,
                () -> DeepResearchStreams.recordFirstResponse(ctx),
                () -> finished.set(true),
                () -> {
                    log.info("Agent has finished, conversationId: {}", conversationsId);
                    finished.set(true);
                    var totalResponseTime = System.currentTimeMillis() - ctx.getStartTime();
                    deps.sessionService().update(
                            ctx.getCurrentSessionId(),
                            buffers.finalAnswer(),
                            buffers.thinking(),
                            null,
                            totalResponseTime,
                            ctx.getFirstResponseTime(),
                            JacksonJson.toJson(ctx.getToolRecords()),
                            null,
                            deps.agentType(),
                            AgentStreamEvent.Reference.of(JacksonJson.toJson(ctx.getAllReferences())).toJSON());
                    ctx.getCompositeDisposable().dispose();
                });
    }
}
