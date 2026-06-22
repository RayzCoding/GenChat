package com.genchat.agent.deepresearch;

import com.genchat.dto.OverAllState;
import com.genchat.dto.PlanTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class DeepResearchPlanLoop {

    private final DeepResearchDependencies deps;
    private final DeepResearchPlanner planner;
    private final DeepResearchTaskExecutor executor;
    private final DeepResearchCritic critic;
    private final DeepResearchReporter reporter;

    public void executeLoopPhase(DeepResearchRunContext ctx,
                                 OverAllState overAllState,
                                 Sinks.Many<String> sink,
                                 AtomicBoolean finished,
                                 StringBuilder finalAnswerBuffer) {
        var voidMono = executeLoop(ctx, overAllState, sink, finished, finalAnswerBuffer);
        Disposable disposable = voidMono.subscribeOn(Schedulers.boundedElastic())
                .subscribe(unused -> {
                        },
                        throwable -> {
                            if (ctx.getCompositeDisposable().isDisposed() || Thread.currentThread().isInterrupted()
                                    || (throwable.getMessage() != null && throwable.getMessage().contains("interrupted"))) {
                                log.info("PlanExecuteAgent 执行被用户停止: {}", throwable.getMessage());
                            } else {
                                log.error("PlanExecuteAgent execute error", throwable);
                                if (finished.compareAndSet(false, true)) {
                                    sink.tryEmitError(throwable);
                                }
                            }
                        });
        ctx.getCompositeDisposable().add(disposable);
    }

    private Mono<Void> executeLoop(DeepResearchRunContext ctx,
                                   OverAllState overAllState,
                                   Sinks.Many<String> sink,
                                   AtomicBoolean finished,
                                   StringBuilder finalAnswerBuffer) {
        return Mono.fromRunnable(() -> {
            try {
                while (overAllState.getRound() < deps.maxRounds() && !ctx.isStopped(finished)) {
                    overAllState.setRound(overAllState.getRound() + 1);
                    log.info("===== Plan-Execute Round {} =====", overAllState.getRound());
                    DeepResearchStreams.emitThinking(sink, finished, "\n🔄The" + overAllState.getRound() + "first round of research began\n");

                    var planTasks = planner.generatePlan(ctx, overAllState, sink, finished);
                    if (ctx.isStopped(finished)) {
                        return;
                    }
                    if (planTasks.isEmpty() || planTasks.stream().allMatch(t -> t.id() == null)) {
                        break;
                    }

                    DeepResearchStreams.emitThinking(sink, finished, "\n--- 开始执行任务 ---\n\n");
                    var resultMap = executor.executePlan(ctx, planTasks, overAllState, sink, finished);
                    if (ctx.isStopped(finished)) {
                        return;
                    }

                    DeepResearchStreams.emitThinking(sink, finished, "\n--- 任务执行完成 ---\n\n");
                    var critique = critic.critique(ctx, overAllState, planTasks, resultMap, sink, finished);
                    if (ctx.isStopped(finished)) {
                        return;
                    }

                    overAllState.add(new AssistantMessage("""
                            【Critique Feedback】
                            %s
                            """.formatted(critique.feedback())));
                    DeepResearchStreams.emitThinking(sink, finished, "\n--- 准备进入下一轮迭代 ---\n");
                    reporter.compressIfNeeded(ctx, overAllState, sink, finished);
                }

                DeepResearchStreams.emitThinking(sink, finished, "\n✅ 研究阶段完成，准备生成最终报告\n");
                reporter.summarizeStream(ctx, overAllState, sink, finished, finalAnswerBuffer);
            } catch (Exception e) {
                if (!ctx.getCompositeDisposable().isDisposed() || Thread.currentThread().isInterrupted()
                        || (e.getMessage() != null && e.getMessage().startsWith("Interrupted"))) {
                    log.info("PlanExecuteAgent 执行被用户停止: {}", e.getMessage());
                    sink.tryEmitNext("{\"type\":\"text\",\"content\":\"⏹ 用户已停止生成\\n\"}");
                    if (finished.compareAndSet(false, true)) {
                        sink.tryEmitComplete();
                    }
                } else {
                    log.error("PlanExecuteAgent 执行异常", e);
                    throw e;
                }
            }
        });
    }
}
