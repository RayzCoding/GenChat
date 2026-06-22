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

        DeepResearchStreams.emitThinking(sink, hasSentFinal, "\n🔍 正在评估当前研究结果...\n");

        if (ctx.isStopped(hasSentFinal)) {
            return new CritiqueResult(true, "任务已取消");
        }

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
                    userMessage.append(String.format("任务 %s: %s\n\n", entry.getKey(), result.output()));
                } else if (result != null && !result.success() && result.error() != null) {
                    userMessage.append(String.format("任务 %s: 执行失败 - %s\n\n", entry.getKey(), result.error()));
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

        String raw = deps.chatClient().prompt(prompt).call().content();
        var result = converter.convert(ThinkTagParser.stripThinkTags(raw));

        if (result.passed()) {
            DeepResearchStreams.emitThinking(sink, hasSentFinal, "\n✅ 研究结果评估通过，准备生成最终报告\n");
        } else {
            DeepResearchStreams.emitThinking(sink, hasSentFinal, "\n⚠️ 研究结果评估未通过，原因分析：" + result.feedback() + "\n");
        }
        return result;
    }
}
