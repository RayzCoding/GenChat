package com.genchat.agent.deepresearch;

import com.genchat.common.prompts.PlanExecutePrompts;
import com.genchat.common.utils.ThinkTagParser;
import com.genchat.dto.CritiqueResult;
import com.genchat.dto.OverAllState;
import com.genchat.dto.PlanTask;
import com.genchat.dto.TaskResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class DeepResearchCritic {

    private final DeepResearchDependencies deps;

    public CritiqueResult critique(DeepResearchRunContext ctx,
                                   OverAllState state,
                                   List<PlanTask> currentPlan,
                                   Map<String, TaskResult> currentResults,
                                   Sinks.Many<String> sink,
                                   AtomicBoolean hasSentFinal) {
        BeanOutputConverter<CritiqueResult> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });

        DeepResearchStreams.emitThinking(sink, hasSentFinal, "\n🔍 Evaluating current research results...\n");

        if (ctx.isStopped(hasSentFinal)) {
            return new CritiqueResult(true, "Task cancelled");
        }

        StringBuilder userMessage = new StringBuilder();
        userMessage.append("【Original User Question】\n");
        userMessage.append(state.getQuestion());
        userMessage.append("\n\n【Research Topic】\n");
        userMessage.append(state.getRefinedResearchTopic() != null ?
                state.getRefinedResearchTopic() : "Research topic not generated");
        userMessage.append("\n\n【Current Round Execution Plan】\n");
        if (currentPlan != null && !currentPlan.isEmpty()) {
            for (PlanTask task : currentPlan) {
                userMessage.append(String.format("- %s\n", task.instruction()));
            }
        } else {
            userMessage.append("None\n");
        }
        userMessage.append("\n\n【Current Round Tool Results】\n");
        if (currentResults != null && !currentResults.isEmpty()) {
            for (Map.Entry<String, TaskResult> entry : currentResults.entrySet()) {
                TaskResult result = entry.getValue();
                if (result != null && result.success() && result.output() != null) {
                    userMessage.append(String.format("Task %s: %s\n\n", entry.getKey(), result.output()));
                } else if (result != null && !result.success() && result.error() != null) {
                    userMessage.append(String.format("Task %s: execution failed - %s\n\n", entry.getKey(), result.error()));
                }
            }
        } else {
            userMessage.append("None\n");
        }

        String prom = PlanExecutePrompts.CRITIQUE + "\n" + converter.getFormat();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + prom),
                new UserMessage(userMessage.toString())
        ));

        String raw = deps.chatClient().prompt(prompt).call().content();
        var result = converter.convert(ThinkTagParser.stripThinkTags(raw));

        if (result.passed()) {
            DeepResearchStreams.emitThinking(sink, hasSentFinal,
                    "\n✅ Research evaluation passed, preparing final report\n");
        } else {
            DeepResearchStreams.emitThinking(sink, hasSentFinal,
                    "\n⚠️ Research evaluation did not pass. Analysis: " + result.feedback() + "\n");
        }
        return result;
    }
}
