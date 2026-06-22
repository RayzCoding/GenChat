package com.genchat.agent;

import com.alibaba.fastjson2.JSON;
import com.genchat.agent.deepresearch.DeepResearchCritic;
import com.genchat.agent.deepresearch.DeepResearchDependencies;
import com.genchat.agent.deepresearch.DeepResearchPlanLoop;
import com.genchat.agent.deepresearch.DeepResearchPlanner;
import com.genchat.agent.deepresearch.DeepResearchPreparation;
import com.genchat.agent.deepresearch.DeepResearchReporter;
import com.genchat.agent.deepresearch.DeepResearchRunContext;
import com.genchat.agent.deepresearch.DeepResearchStreams;
import com.genchat.agent.deepresearch.DeepResearchTaskExecutor;
import com.genchat.common.AgentStreamEvent;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DeepResearchAgent {

    private final DeepResearchDependencies deps;
    private final DeepResearchPreparation preparation;
    private final DeepResearchPlanLoop planLoop;

    public DeepResearchAgent(AiChatSessionService sessionService,
                             ChatModel chatModel,
                             List<ToolCallback> tools,
                             AgentTaskService agentTaskService,
                             int maxRounds) {
        this.deps = new DeepResearchDependencies(sessionService, chatModel, tools, agentTaskService, maxRounds);
        this.preparation = new DeepResearchPreparation(deps);
        var planner = new DeepResearchPlanner(deps);
        var executor = new DeepResearchTaskExecutor(deps);
        var critic = new DeepResearchCritic(deps);
        var reporter = new DeepResearchReporter(deps);
        this.planLoop = new DeepResearchPlanLoop(deps, planner, executor, critic, reporter);
    }

    public void initPersistentChatMemory(String conversationId) {
        preparation.initPersistentChatMemory(conversationId);
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
        var overAllState = preparation.initStateAndSaveQuestion(ctx, conversationsId, question);
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
