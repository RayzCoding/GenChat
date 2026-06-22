package com.genchat.agent.deepresearch;

import com.genchat.agent.SimpleReactAgent;
import com.genchat.common.prompts.PlanExecutePrompts;
import com.genchat.dto.PlanTask;
import com.genchat.dto.TaskResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class DeepResearchTaskExecutor {

    private final DeepResearchDependencies deps;

    public Map<String, TaskResult> executePlan(DeepResearchRunContext ctx,
                                               List<PlanTask> plan,
                                               com.genchat.dto.OverAllState state,
                                               Sinks.Many<String> sink,
                                               AtomicBoolean hasSentFinal) {

        Map<String, TaskResult> results = new ConcurrentHashMap<>();
        Map<Integer, List<PlanTask>> grouped = plan.stream().collect(Collectors.groupingBy(PlanTask::order));
        Map<String, String> accumulatedResults = new ConcurrentHashMap<>();

        for (Integer order : new TreeSet<>(grouped.keySet())) {
            if (ctx.isStopped(hasSentFinal)) {
                break;
            }

            String dependencyContext = buildDependencyContext(accumulatedResults, plan, order);
            List<PlanTask> tasks = grouped.get(order);
            CountDownLatch latch = new CountDownLatch(tasks.size());

            for (PlanTask task : tasks) {
                var taskDisposable = reactor.core.publisher.Mono.fromRunnable(() -> {
                            boolean acquired = false;
                            try {
                                if (ctx.getCompositeDisposable().isDisposed()) {
                                    return;
                                }
                                deps.toolSemaphore().acquire();
                                acquired = true;

                                if (task == null || task.id() == null || task.id().isEmpty()) {
                                    return;
                                }
                                if (ctx.getCompositeDisposable().isDisposed()) {
                                    return;
                                }

                                TaskResult result = executeWithRetry(ctx, task, dependencyContext, sink, hasSentFinal);
                                results.put(task.id(), result);

                                if (result.success() && result.output() != null) {
                                    accumulatedResults.put(task.id(), result.output());
                                }

                                StringBuilder resultMessage = new StringBuilder();
                                resultMessage.append("【Completed Task Result】\n");
                                resultMessage.append("taskId: ").append(task.id()).append("\n");
                                resultMessage.append("success: ").append(result.success()).append("\n");
                                if (result.output() != null) {
                                    resultMessage.append("result:\n").append(result.output()).append("\n");
                                }
                                if (result.error() != null) {
                                    resultMessage.append("error:\n").append(result.error()).append("\n");
                                }
                                resultMessage.append("【End Task Result】");
                                state.add(new AssistantMessage(resultMessage.toString()));

                            } catch (InterruptedException e) {
                                log.info("Task {} 执行被中断", task.id());
                                Thread.currentThread().interrupt();
                                results.put(task.id(), new TaskResult(task.id(), false, null, "Task execution interrupted"));
                            } catch (Exception e) {
                                if (ctx.getCompositeDisposable().isDisposed() || Thread.currentThread().isInterrupted()
                                        || (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
                                    log.info("Task {} 执行被用户停止: {}", task.id(), e.getMessage());
                                    results.put(task.id(), new TaskResult(task.id(), false, null, "Task execution interrupted by user"));
                                } else {
                                    log.error("Task execution error", e);
                                    results.put(task.id(), new TaskResult(task.id(), false, null, "Task execution error: " + e.getMessage()));
                                }
                            } finally {
                                if (acquired) {
                                    deps.toolSemaphore().release();
                                }
                                latch.countDown();
                            }
                        })
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                        .subscribe();
                ctx.getCompositeDisposable().add(taskDisposable);
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("executePlan interrupted");
                break;
            }
        }
        return results;
    }

    TaskResult executeWithRetry(DeepResearchRunContext ctx,
                                PlanTask task,
                                String dependencyContext,
                                Sinks.Many<String> sink,
                                AtomicBoolean hasSentFinal) {

        Throwable lastError = null;
        DeepResearchStreams.emitThinking(sink, hasSentFinal, "⚙️ 正在执行任务 " + task.id() + " : " + task.instruction() + "\n");

        if (ctx.isStopped(hasSentFinal)) {
            return new TaskResult(task.id(), false, null, "任务被用户停止");
        }
        try {
            String fullContext = """
                                【Available Results】
                                %s
                    
                                【Current Task】
                                %s
                    """.formatted(dependencyContext, task.instruction());

            var simpleReactAgent = new SimpleReactAgent(deps.chatModel(), deps.tools());
            simpleReactAgent.setMaxRounds(5);
            simpleReactAgent.setSystemPrompt(PlanExecutePrompts.EXECUTE);
            var result = simpleReactAgent.executeInternal(null, fullContext, true);

            if (ctx.getCompositeDisposable().isDisposed()) {
                return new TaskResult(task.id(), false, null, "任务被用户停止");
            }

            if (result.getSearchResults() != null && !result.getSearchResults().isEmpty()) {
                synchronized (ctx.getAllReferences()) {
                    ctx.getAllReferences().addAll(result.getSearchResults());
                }
            }

            String answer = result.getAnswer();
            DeepResearchStreams.emitThinking(sink, hasSentFinal, "执行结果: " + answer + "\n\n");
            return new TaskResult(task.id(), true, answer, null);
        } catch (Exception e) {
            if (ctx.getCompositeDisposable().isDisposed() || Thread.currentThread().isInterrupted()
                    || (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
                log.info("Task {} 执行被用户停止: {}", task.id(), e.getMessage());
                return new TaskResult(task.id(), false, null, "任务被用户停止");
            }
            lastError = e;
            log.warn("Task {} failed: {}", task.id(), e.getMessage());
        }

        DeepResearchStreams.emitThinking(sink, hasSentFinal, "\n❌ 任务 " + task.id() + " 执行失败: " + (lastError == null ? "unknown error" : lastError.getMessage()) + "\n\n");
        return new TaskResult(task.id(), false, null, lastError == null ? "unknown error" : lastError.getMessage());
    }

    private String buildDependencyContext(Map<String, String> results, List<PlanTask> plan, int currentOrder) {
        StringBuilder context = new StringBuilder();
        if (currentOrder == 1) {
            return context.append("无\n").toString();
        }

        boolean hasDependencies = false;
        for (Map.Entry<String, String> entry : results.entrySet()) {
            PlanTask task = plan.stream()
                    .filter(t -> t.id() != null && t.id().equals(entry.getKey()))
                    .findFirst()
                    .orElse(null);

            if (task != null && task.order() == currentOrder - 1) {
                if (!hasDependencies) {
                    context.append("任务 ");
                    hasDependencies = true;
                }
                context.append(String.format("%s: %s\n\n", entry.getKey(), entry.getValue()));
            }
        }

        if (!hasDependencies) {
            context.append("无\n");
        }
        return context.toString();
    }
}
