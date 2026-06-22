package com.genchat.agent;

import com.alibaba.fastjson2.JSON;
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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class DeepResearchAgent {

    private final DeepResearchDependencies deps;
    private final DeepResearchPreparation preparation;
    private final DeepResearchPlanLoop planLoop;

    private ChatMemory chatMemory;

    public void initPersistentChatMemory(String conversationId) {
        this.chatMemory = preparation.buildChatMemory(conversationId);
    }

    public Flux<String> stream(String conversationsId, String question) {
        if (deps.agentTaskService().hasRunningTask(conversationsId)) {
            return Flux.error(new IllegalStateException("Agent is already running"));
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        var taskInfo = deps.agentTaskService().registerTask(conversationsId, sink, deps.agentType());
        if (Objects.isNull(taskInfo)) {
            return Flux.error(new IllegalStateException(new IllegalStateException(
                    "The conversation is currently in progress, Please try again later")));
        }

        var ctx = new DeepResearchRunContext(deps);
        DeepResearchStreams.initTimers(ctx);
        ctx.getToolRecords().clear();
        ctx.setCompositeDisposable(Disposables.composite());
        ctx.getAllReferences().clear();

        var finished = new AtomicBoolean(false);
        var overAllState = preparation.initStateAndSaveQuestion(ctx, conversationsId, question, chatMemory);
        var finalAnswerBuffer = new StringBuilder();
        var thinkingBuffer = new StringBuilder();

        preparation.clarifyRequirement(ctx, overAllState, sink, finished,
                () -> preparation.generateResearchTopicPhase(ctx, overAllState, sink, finished,
                        () -> planLoop.executeLoopPhase(ctx, overAllState, sink, finished, finalAnswerBuffer)));

        deps.agentTaskService().setDisposable(conversationsId, ctx.getCompositeDisposable());

        return sink.asFlux()
                .doOnNext(chunk -> {
                    DeepResearchStreams.recordFirstResponse(ctx);
                    DeepResearchStreams.parseAndAppendToBuffers(chunk, finalAnswerBuffer, thinkingBuffer);
                })
                .doOnCancel(() -> {
                    finished.set(true);
                    deps.agentTaskService().stopTask(conversationsId);
                })
                .doFinally(signalType -> {
                    log.info("Agent has finished, conversationId: {}", conversationsId);
                    finished.set(true);
                    var totalResponseTime = System.currentTimeMillis() - ctx.getStartTime();
                    deps.sessionService().update(
                            ctx.getCurrentSessionId(),
                            finalAnswerBuffer,
                            thinkingBuffer,
                            null,
                            totalResponseTime,
                            ctx.getFirstResponseTime(),
                            JSON.toJSONString(ctx.getToolRecords()),
                            null,
                            deps.agentType(),
                            AgentStreamEvent.Reference.of(JSON.toJSONString(ctx.getAllReferences())).toJSON());
                    deps.agentTaskService().stopTask(conversationsId);
                    ctx.getCompositeDisposable().dispose();
                });
    }
}
