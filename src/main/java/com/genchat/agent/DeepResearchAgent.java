package com.genchat.agent;

import com.alibaba.fastjson2.JSON;
import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.PlanExecutePrompts;
import com.genchat.common.utils.ThinkTagParser;
import com.genchat.dto.*;
import com.genchat.entity.SearchResult;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
public class DeepResearchAgent {
    private final AiChatSessionService sessionService;
    private final ChatModel chatModel;
    private final AgentTaskService agentTaskService;
    private final List<ToolCallback> tools;
    private final ChatClient chatClient;
    private final int maxRounds;
    protected Long currentSessionId;
    private ChatMemory chatMemory;
    protected String agentType;
    protected Set<String> usedTools;
    protected long startTime;
    private final Semaphore toolSemaphore;
    private int contextCharLimit = 50000;

    protected long firstResponseTime;
    private Disposable.Composite compositeDisposable;
    private List<SearchResult> allReferences;

    public DeepResearchAgent(AiChatSessionService sessionService,
                             ChatModel chatModel,
                             List<ToolCallback> tools,
                             AgentTaskService agentTaskService,
                             int maxRounds) {
        this.agentType = "DeepResearchAgent";
        this.sessionService = sessionService;
        this.toolSemaphore = new Semaphore(3);
        this.chatModel = chatModel;
        this.tools = tools;
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
        allReferences = new ArrayList<>();

        // start flow: clarify requirement-> generate research topic -> execute loop
        clarifyRequirement(overAllState, sink, finished,
                () -> generateResearchTopicPhase(overAllState, sink, finished,
                        () -> executeLoopPhase(overAllState, sink, finished, finalAnswerBuffer)));
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
                            String.join(",", usedTools), null, agentType,
                            AgentResponse.reference(JSON.toJSONString(allReferences)));
                    agentTaskService.stopTask(conversationsId);
                    compositeDisposable.dispose();
                });
    }

    private void executeLoopPhase(OverAllState overAllState,
                                  Sinks.Many<String> sink,
                                  AtomicBoolean finished,
                                  StringBuilder finalAnswerBuffer) {
        var voidMono = executeLoop(overAllState, sink, finished, finalAnswerBuffer);
        var disposable = voidMono.subscribeOn(Schedulers.boundedElastic())
                .subscribe(unused -> {
                        },
                        throwable -> {
                            if (compositeDisposable.isDisposed() || Thread.currentThread().isInterrupted()
                                    || (throwable.getMessage() != null && throwable.getMessage().contains("interrupted"))) {
                                log.info("PlanExecuteAgent 执行被用户停止: {}", throwable.getMessage());
                            } else {
                                log.error("PlanExecuteAgent execute error", throwable);
                                if (finished.compareAndSet(false, true)) {
                                    sink.tryEmitError(throwable);
                                }
                            }
                        });

        compositeDisposable.add(disposable);
    }

    private Mono<Void> executeLoop(OverAllState overAllState,
                                   Sinks.Many<String> sink,
                                   AtomicBoolean finished,
                                   StringBuilder finalAnswerBuffer) {
        return Mono.fromRunnable(() -> {
            try {
                while (overAllState.getRound() < maxRounds && !finished.get() && !compositeDisposable.isDisposed()) {
                    overAllState.setRound(overAllState.getRound() + 1);
                    log.info("===== Plan-Execute Round {} =====", overAllState.getRound());
                    emitThinking(sink, finished, "\n🔄The" + overAllState.getRound() + "first round of research began\n");
                    var planTasks = generatePlan(overAllState, sink, finished);
                    if (finished.get() || compositeDisposable.isDisposed()) {
                        return;
                    }
                    if (planTasks.isEmpty() || planTasks.stream().allMatch(t -> t.id() == null)) {
                        break;
                    }
                    emitThinking(sink, finished, "\n--- 开始执行任务 ---\n\n");
                    // executePlan
                    var resultMap = executePlan(planTasks, overAllState, sink, finished);
                    if (finished.get() || compositeDisposable.isDisposed()) {
                        return;
                    }
                    emitThinking(sink, finished, "\n--- 任务执行完成 ---\n\n");
                    // critique
                    var critique = critique(overAllState, planTasks, resultMap, sink, finished);
                    if (finished.get() || compositeDisposable.isDisposed()) {
                        return;
                    }
                    overAllState.add(new AssistantMessage("""
                            【Critique Feedback】
                            %s
                            """.formatted(critique.feedback())));
                    emitThinking(sink, finished, "\n--- 准备进入下一轮迭代 ---\n");
                    // compressIfNeeded
                    compressIfNeeded(overAllState, sink, finished);
                }
                emitThinking(sink, finished, "\n✅ 研究阶段完成，准备生成最终报告\n");
                // summarizeStream
                summarizeStream(overAllState, sink, finished, finalAnswerBuffer);
            } catch (Exception e) {
                if (!compositeDisposable.isDisposed() || Thread.currentThread().isInterrupted()
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

    private void summarizeStream(OverAllState state,
                                 Sinks.Many<String> sink,
                                 AtomicBoolean finished,
                                 StringBuilder finalAnswerBuffer) {

        emitThinking(sink, finished, "\n📝 正在生成最终研究报告...\n\n");

        final boolean[] summarizeInThinkHolder = {false};

        // 提取工具执行结果，排除中间过程
        String toolResults = state.extractToolResults();

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + PlanExecutePrompts.SUMMARIZE),
                new UserMessage("""
                                        【用户原始问题】
                                        %s
                        
                                        【研究主题】
                                        %s
                        
                                        【工具检索结果】
                                        %s
                        """.formatted(
                        state.getQuestion(),
                        state.getRefinedResearchTopic() != null ? state.getRefinedResearchTopic() : "未生成研究主题",
                        toolResults.isEmpty() ? "（未检索到相关结果）" : toolResults
                ))
        ));

        Disposable disposable = chatClient.prompt()
                .messages(prompt.getInstructions())
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    if (finished.get() || compositeDisposable.isDisposed()) {
                        return;
                    }

                    if (chunk == null
                            || chunk.getResult() == null
                            || chunk.getResult().getOutput() == null) {
                        return;
                    }

                    String text = chunk.getResult().getOutput().getText();
                    if (text == null) {
                        return;
                    }

                    ThinkTagParser.ParseResult parseResult = ThinkTagParser.parse(text, summarizeInThinkHolder[0]);
                    summarizeInThinkHolder[0] = parseResult.inThink();
                    for (ThinkTagParser.Segment segment : parseResult.segments()) {
                        if (segment.thinking()) {
                            emitThinking(sink, finished, segment.content());
                        } else {
                            finalAnswerBuffer.append(segment.content());
                            if (finished.get()) {
                                return;
                            }
                            sink.tryEmitNext(AgentResponse.text(segment.content()));
                        }
                    }
                })
                .doOnComplete(() -> {
                    // 在 text 输出后，输出参考来源
                    if (!allReferences.isEmpty()) {
                        sink.tryEmitNext(AgentResponse.reference(JSON.toJSONString(allReferences)));
                    }

                    if (finished.compareAndSet(false, true)) {
                        sink.tryEmitComplete();
                    }
                })
                .doOnError(e -> {
                    if (finished.compareAndSet(false, true)) {
                        sink.tryEmitError(e);
                    }
                })
                .subscribe();

        // 将summarizeStream的disposable添加到composite
        compositeDisposable.add(disposable);
    }

    private void compressIfNeeded(OverAllState state, Sinks.Many<String> sink, AtomicBoolean hasSentFinal) {
        if (state.currentChars() < contextCharLimit) {
            return;
        }

        log.warn("===== Context too large, compressing ,size is {} =====", state.currentChars());

        emitThinking(sink, hasSentFinal, "📦 上下文过长，正在压缩...\n");

        if (hasSentFinal.get() || compositeDisposable.isDisposed()) {
            return;
        }

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + """
                        ##最大压缩限制（必须遵守）
                        -你输出的最终内容【总字符数（包含所有标签、空格、换行）】
                        不得超过：%s
                                - 这是硬性上限，不是建议
                                - 如超过该限制，视为压缩失败
                        
                        """.formatted(contextCharLimit) + PlanExecutePrompts.COMPRESS),

                new UserMessage(state.renderFullContext())
        ));

        String snapshot = chatModel.call(prompt)
                .getResult()
                .getOutput()
                .getText();

        state.getMessages().clear();
        state.add(new SystemMessage("【Compressed Agent State】\n" + snapshot));
        log.warn("===== Context compress has completed, size is {} =====", state.currentChars());

        emitThinking(sink, hasSentFinal, "✅ 上下文压缩完成\n");
    }

    private Map<String, TaskResult> executePlan(List<PlanTask> plan,
                                                OverAllState state,
                                                Sinks.Many<String> sink,
                                                AtomicBoolean hasSentFinal) {

        Map<String, TaskResult> results = new ConcurrentHashMap<>();

        // 按 order 分组：order 相同的 task 可并行
        Map<Integer, List<PlanTask>> grouped = plan.stream().collect(Collectors.groupingBy(PlanTask::order));

        Map<String, String> accumulatedResults = new ConcurrentHashMap<>();

        // 按 order 顺序执行（不同 order 串行）
        for (Integer order : new TreeSet<>(grouped.keySet())) {
            if (hasSentFinal.get() || compositeDisposable.isDisposed()) {
                break;
            }

            // 构建任务执行的依赖上下文（只传递上一个 order 的结果）
            String dependencyContext = buildDependencyContext(accumulatedResults, plan, order);

            List<PlanTask> tasks = grouped.get(order);

            // 使用CountDownLatch等待当前order组全部完成
            CountDownLatch latch = new CountDownLatch(tasks.size());

            for (PlanTask task : tasks) {
                // 使用Mono包装任务执行
                Disposable taskDisposable = Mono.fromRunnable(() -> {
                            boolean acquired = false;
                            try {
                                // 检查是否已被停止
                                if (compositeDisposable.isDisposed()) {
                                    return;
                                }

                                // 获取执行许可
                                toolSemaphore.acquire();
                                acquired = true;

                                if (task == null || task.id() == null || task.id().isEmpty()) {
                                    return;
                                }

                                // 再次检查，避免在acquire后被停止
                                if (compositeDisposable.isDisposed()) {
                                    return;
                                }

                                TaskResult result = executeWithRetry(task, dependencyContext, sink, hasSentFinal);
                                results.put(task.id(), result);

                                if (result.success() && result.output() != null) {
                                    accumulatedResults.put(task.id(), result.output());
                                }

                                // 构建任务结果消息，只在有错误时才显示 error
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

                                results.put(task.id(),
                                        new TaskResult(
                                                task.id(),
                                                false,
                                                null,
                                                "Task execution interrupted"
                                        ));
                            } catch (Exception e) {
                                // 检查是否是中断导致的异常
                                if (compositeDisposable.isDisposed() || Thread.currentThread().isInterrupted()
                                        || (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
                                    log.info("Task {} 执行被用户停止: {}", task.id(), e.getMessage());
                                    results.put(task.id(),
                                            new TaskResult(
                                                    task.id(),
                                                    false,
                                                    null,
                                                    "Task execution interrupted by user"
                                            ));
                                } else {
                                    log.error("Task execution error", e);
                                    results.put(task.id(),
                                            new TaskResult(
                                                    task.id(),
                                                    false,
                                                    null,
                                                    "Task execution error: " + e.getMessage()
                                            ));
                                }
                            } finally {
                                // 释放许可
                                if (acquired) {
                                    toolSemaphore.release();
                                }
                                latch.countDown();
                            }
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();

                // 将任务的disposable添加到composite
                compositeDisposable.add(taskDisposable);
            }

            // 等待当前order组全部完成
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

    /**
     * 执行单个任务（带上下文）
     * 上下文格式：【Available Results】\n[依赖结果]\n\n【Current Task】\n[任务指令]
     *
     * @param task              要执行的任务
     * @param dependencyContext 依赖上下文（只包含依赖结果）
     * @param sink              响应流
     * @param hasSentFinal      是否已发送最终结果
     * @return 任务执行结果
     */
    private TaskResult executeWithRetry(PlanTask task, String dependencyContext,
                                        Sinks.Many<String> sink, AtomicBoolean hasSentFinal) {

        Throwable lastError = null;
        emitThinking(sink, hasSentFinal, "⚙️ 正在执行任务 " + task.id() + " : " + task.instruction() + "\n");

        // 检查是否已被停止
        if (hasSentFinal.get() || compositeDisposable.isDisposed()) {
            return new TaskResult(task.id(), false, null, "任务被用户停止");
        }
        try {
            // 构建完整任务上下文（依赖 + 当前任务指令）
            String fullContext = """
                                【Available Results】
                                %s
                    
                                【Current Task】
                                %s
                    """.formatted(
                    dependencyContext,
                    task.instruction()
            );

            var simpleReactAgent = new SimpleReactAgent(chatModel, tools);
            simpleReactAgent.setMaxRounds(5);
            simpleReactAgent.setSystemPrompt(PlanExecutePrompts.EXECUTE);
            var result = simpleReactAgent.executeInternal(null, fullContext, true);

            if (compositeDisposable.isDisposed()) {
                return new TaskResult(task.id(), false, null, "任务被用户停止");
            }

            // 收集搜索结果到 allReferences
            if (result.getSearchResults() != null && !result.getSearchResults().isEmpty()) {
                synchronized (allReferences) {
                    allReferences.addAll(result.getSearchResults());
                }
            }

            String answer = result.getAnswer();
            emitThinking(sink, hasSentFinal, "执行结果: " + answer + "\n\n");
            return new TaskResult(task.id(), true, answer, null);
        } catch (Exception e) {
            // 检查是否是中断导致的异常
            if (compositeDisposable.isDisposed() || Thread.currentThread().isInterrupted()
                    || (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
                log.info("Task {} 执行被用户停止: {}", task.id(), e.getMessage());
                return new TaskResult(task.id(), false, null, "任务被用户停止");
            }
            lastError = e;
            log.warn("Task {} failed: {}", task.id(), e.getMessage());
        }

        // 执行失败
        emitThinking(sink, hasSentFinal, "\n❌ 任务 " + task.id() + " 执行失败: " + (lastError == null ? "unknown error" : lastError.getMessage()) + "\n\n");
        return new TaskResult(
                task.id(),
                false,
                null,
                lastError == null ? "unknown error" : lastError.getMessage()
        );
    }

    /**
     * 构建任务执行的依赖上下文
     * 规则：同 order 的任务不传依赖（并行），不同 order 的任务只传递上一个 order 的结果
     * 注意：此方法只返回【Available Results】部分，【Current Task】由 executeWithRetry 拼接
     *
     * @param results      所有已完成任务的结果
     * @param plan         当前轮次的执行计划（用于获取任务 order）
     * @param currentOrder 当前任务的 order
     * @return 依赖上下文字符串
     */
    private String buildDependencyContext(Map<String, String> results, List<PlanTask> plan, int currentOrder) {
        StringBuilder context = new StringBuilder();

        // 1. 第一个 order 的任务没有依赖
        if (currentOrder == 1) {
            return context.append("无\n").toString();
        }

        // 2. 收集上一个 order 的任务结果
        boolean hasDependencies = false;

        for (Map.Entry<String, String> entry : results.entrySet()) {
            // 查找任务对应的 order
            PlanTask task = plan.stream()
                    .filter(t -> t.id() != null && t.id().equals(entry.getKey()))
                    .findFirst()
                    .orElse(null);

            if (task != null && task.order() == currentOrder - 1) {
                // 只有上一个 order 的结果才是依赖
                if (!hasDependencies) {
                    context.append("任务 ");
                    hasDependencies = true;
                }
                context.append(String.format("%s: %s\n\n",
                        entry.getKey(),
                        entry.getValue()));
            }
        }

        if (!hasDependencies) {
            context.append("无\n");
        }

        return context.toString();
    }

    private CritiqueResult critique(OverAllState state, List<PlanTask> currentPlan,
                                    Map<String, TaskResult> currentResults,
                                    Sinks.Many<String> sink, AtomicBoolean hasSentFinal) {
        BeanOutputConverter<CritiqueResult> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });

        emitThinking(sink, hasSentFinal, "\n🔍 正在评估当前研究结果...\n");

        if (hasSentFinal.get() || compositeDisposable.isDisposed()) {
            return new CritiqueResult(true, "任务已取消");
        }

        // 构建批判的用户消息（只包含当前轮次的上下文）
        StringBuilder userMessage = new StringBuilder();
        userMessage.append("【用户原始问题】\n");
        userMessage.append(state.getQuestion());

        userMessage.append("\n\n【研究主题】\n");
        userMessage.append(state.getRefinedResearchTopic() != null ?
                state.getRefinedResearchTopic() : "未生成研究主题");

        userMessage.append("\n\n【当前轮次的执行计划】\n");
        if (currentPlan != null && !currentPlan.isEmpty()) {
            for (PlanTask task : currentPlan) {
                userMessage.append(String.format("- %s\n", task.instruction()));
            }
        } else {
            userMessage.append("无\n");
        }

        userMessage.append("\n\n【当前轮次的工具结果】\n");
        if (currentResults != null && !currentResults.isEmpty()) {
            for (Map.Entry<String, TaskResult> entry : currentResults.entrySet()) {
                TaskResult result = entry.getValue();
                if (result != null && result.success() && result.output() != null) {
                    userMessage.append(String.format("任务 %s: %s\n\n",
                            entry.getKey(), result.output()));
                } else if (result != null && !result.success() && result.error() != null) {
                    userMessage.append(String.format("任务 %s: 执行失败 - %s\n\n",
                            entry.getKey(), result.error()));
                }
            }
        } else {
            userMessage.append("无\n");
        }

        String prom = PlanExecutePrompts.CRITIQUE + "\n" + converter.getFormat();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + prom),
                new UserMessage(userMessage.toString())
        ));

        String raw = chatClient.prompt(prompt).call().content();

        var result = converter.convert(ThinkTagParser.stripThinkTags(raw));

        if (result.passed()) {
            emitThinking(sink, hasSentFinal, "\n✅ 研究结果评估通过，准备生成最终报告\n");
        } else {
            emitThinking(sink, hasSentFinal, "\n⚠️ 研究结果评估未通过，原因分析：" + result.feedback() + "\n");
        }

        return result;
    }

    private List<PlanTask> generatePlan(OverAllState overAllState, Sinks.Many<String> sink, AtomicBoolean finished) {
        var toolDescriptions = renderToolDescriptions();
        var converter = new BeanOutputConverter<List<PlanTask>>(new ParameterizedTypeReference<>() {
        });
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + PlanExecutePrompts.PLAN + """
                                                ## 当前上下文
                                                当前轮次: %s
                        
                                                ## 可用工具说明（仅用于规划参考）
                                                %s
                        
                                                ## 输出格式
                                                %s
                        """.formatted(overAllState.getRound(), toolDescriptions, converter.getFormat())),
                new UserMessage("""
                        【研究主题】
                        %s
                        
                        【对话历史】
                        %s
                        
                        ## 重要约束
                        如果会话历史中存在【Critique Feedback】，你必须：
                        1. 仔细分析反馈中指出的不足
                        2. 新的计划必须直接解决这些问题
                        3. 不要重复之前失败的尝试
                        """.formatted(
                        overAllState.getRefinedResearchTopic() != null ?
                                overAllState.getRefinedResearchTopic() : overAllState.getQuestion(),
                        overAllState.renderFullContext()
                ))));
        emitThinking(sink, finished, "📋 An execution plan is being generated...\n");
        if (finished.get() || compositeDisposable.isDisposed()) {
            return List.of();
        }
        var content = chatClient.prompt()
                .messages(prompt.getInstructions())
                .call()
                .content();
        var planTasks = converter.convert(ThinkTagParser.stripThinkTags(content));
        emitThinking(sink, finished, "\n✅ 执行计划已生成，共 " + planTasks.size() + " 个任务\n");

        if (!planTasks.isEmpty()) {
            var planText = new StringBuilder("\n📋 执行计划表：\n");
            for (var planTask : planTasks) {
                planText.append(String.format("  🟠 %s \n", planTask.instruction()));
            }
            emitThinking(sink, finished, planText.toString());
        }
        return planTasks;
    }

    private String renderToolDescriptions() {
        if (tools == null || tools.isEmpty()) {
            return "（Currently, no tools are available）";
        }

        StringBuilder sb = new StringBuilder();
        for (ToolCallback tool : tools) {
            sb.append("- ")
                    .append(tool.getToolDefinition().name())
                    .append(": ")
                    .append(tool.getToolDefinition().description())
                    .append("\n");
        }
        return sb.toString();
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
                                            Runnable onComplete) {
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
                    emitThinking(sink, finished, "\n✅ The research topic has been generated\n\n");
                    onComplete.run();
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
