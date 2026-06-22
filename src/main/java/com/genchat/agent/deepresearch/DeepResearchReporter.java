package com.genchat.agent.deepresearch;

import com.alibaba.fastjson2.JSON;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.prompts.PlanExecutePrompts;
import com.genchat.common.utils.ThinkTagParser;
import com.genchat.dto.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class DeepResearchReporter {

    private final DeepResearchDependencies deps;

    public void summarizeStream(DeepResearchRunContext ctx,
                                OverAllState state,
                                Sinks.Many<String> sink,
                                AtomicBoolean finished,
                                StringBuilder finalAnswerBuffer) {
        DeepResearchStreams.emitThinking(sink, finished, "\n📝 正在生成最终研究报告...\n\n");

        final boolean[] summarizeInThinkHolder = {false};
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

        Disposable disposable = deps.chatClient().prompt()
                .messages(prompt.getInstructions())
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    if (ctx.isStopped(finished)) {
                        return;
                    }
                    if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
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
                            DeepResearchStreams.emitThinking(sink, finished, segment.content());
                        } else {
                            finalAnswerBuffer.append(segment.content());
                            if (finished.get()) {
                                return;
                            }
                            sink.tryEmitNext(new AgentStreamEvent.Text(segment.content()).toJSON());
                        }
                    }
                })
                .doOnComplete(() -> {
                    if (!ctx.getAllReferences().isEmpty()) {
                        sink.tryEmitNext(AgentStreamEvent.Reference.of(JSON.toJSONString(ctx.getAllReferences())).toJSON());
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

        ctx.getCompositeDisposable().add(disposable);
    }

    public void compressIfNeeded(DeepResearchRunContext ctx,
                                 OverAllState state,
                                 Sinks.Many<String> sink,
                                 AtomicBoolean hasSentFinal) {
        if (state.currentChars() < ctx.getContextCharLimit()) {
            return;
        }

        log.warn("===== Context too large, compressing ,size is {} =====", state.currentChars());
        DeepResearchStreams.emitThinking(sink, hasSentFinal, "📦 上下文过长，正在压缩...\n");

        if (ctx.isStopped(hasSentFinal)) {
            return;
        }

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + """
                        ##最大压缩限制（必须遵守）
                        -你输出的最终内容【总字符数（包含所有标签、空格、换行）】
                        不得超过：%s
                                - 这是硬性上限，不是建议
                                - 如超过该限制，视为压缩失败
                        
                        """.formatted(ctx.getContextCharLimit()) + PlanExecutePrompts.COMPRESS),
                new UserMessage(state.renderFullContext())
        ));

        String snapshot = deps.chatModel().call(prompt)
                .getResult()
                .getOutput()
                .getText();

        state.getMessages().clear();
        state.add(new SystemMessage("【Compressed Agent State】\n" + snapshot));
        log.warn("===== Context compress has completed, size is {} =====", state.currentChars());
        DeepResearchStreams.emitThinking(sink, hasSentFinal, "✅ 上下文压缩完成\n");
    }
}
