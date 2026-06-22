package com.genchat.agent.deepresearch;

import com.genchat.common.prompts.PlanExecutePrompts;
import com.genchat.common.utils.ThinkTagParser;
import com.genchat.dto.OverAllState;
import com.genchat.dto.PlanTask;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class DeepResearchPlanner {

    private final DeepResearchDependencies deps;

    public List<PlanTask> generatePlan(DeepResearchRunContext ctx,
                                       OverAllState overAllState,
                                       Sinks.Many<String> sink,
                                       AtomicBoolean finished) {
        var toolDescriptions = DeepResearchStreams.renderToolDescriptions(deps.tools());
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
        DeepResearchStreams.emitThinking(sink, finished, "📋 An execution plan is being generated...\n");
        if (ctx.isStopped(finished)) {
            return List.of();
        }
        var content = deps.chatClient().prompt()
                .messages(prompt.getInstructions())
                .call()
                .content();
        var planTasks = converter.convert(ThinkTagParser.stripThinkTags(content));
        DeepResearchStreams.emitThinking(sink, finished, "\n✅ 执行计划已生成，共 " + planTasks.size() + " 个任务\n");

        if (!planTasks.isEmpty()) {
            var planText = new StringBuilder("\n📋 执行计划表：\n");
            for (var planTask : planTasks) {
                planText.append(String.format("  🟠 %s \n", planTask.instruction()));
            }
            DeepResearchStreams.emitThinking(sink, finished, planText.toString());
        }
        return planTasks;
    }
}
