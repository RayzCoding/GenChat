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
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepResearchReporter {

    private final DeepResearchDependencies deps;

    public void summarizeStream(DeepResearchRunContext ctx,
                                OverAllState state,
                                Sinks.Many<String> sink,
                                AtomicBoolean finished,
                                StringBuilder finalAnswerBuffer) {
        DeepResearchStreams.emitThinking(sink, finished, "\n📝 Generating final research report...\n\n");

        final boolean[] summarizeInThinkHolder = {false};
        String toolResults = state.extractToolResults();

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + PlanExecutePrompts.SUMMARIZE),
                new UserMessage("""
                                        【Original User Question】
                                        %s
                        
                                        【Research Topic】
                                        %s
                        
                                        【Tool Retrieval Results】
                                        %s
                        """.formatted(
                        state.getQuestion(),
                        state.getRefinedResearchTopic() != null ? state.getRefinedResearchTopic() : "Research topic not generated",
                        toolResults.isEmpty() ? "(No relevant results retrieved)" : toolResults
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

        log.warn("===== Context too large, compressing, size is {} =====", state.currentChars());
        DeepResearchStreams.emitThinking(sink, hasSentFinal, "📦 Context too large, compressing...\n");

        if (ctx.isStopped(hasSentFinal)) {
            return;
        }

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + """
                        ## Maximum compression limit (mandatory)
                        - Total character count of your output (including tags, spaces, newlines)
                        must not exceed: %s
                        - This is a hard limit, not a suggestion
                        - Exceeding it is considered compression failure
                        
                        """.formatted(ctx.getContextCharLimit()) + PlanExecutePrompts.COMPRESS),
                new UserMessage(state.renderFullContext())
        ));

        String snapshot = deps.chatModel().call(prompt)
                .getResult()
                .getOutput()
                .getText();

        state.getMessages().clear();
        state.add(new SystemMessage("【Compressed Agent State】\n" + snapshot));
        log.warn("===== Context compression completed, size is {} =====", state.currentChars());
        DeepResearchStreams.emitThinking(sink, hasSentFinal, "✅ Context compression completed\n");
    }
}
