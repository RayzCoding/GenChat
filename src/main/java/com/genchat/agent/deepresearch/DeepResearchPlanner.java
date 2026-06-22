package com.genchat.agent.deepresearch;

import com.genchat.common.prompts.PlanExecutePrompts;
import com.genchat.common.utils.ThinkTagParser;
import com.genchat.dto.OverAllState;
import com.genchat.dto.PlanTask;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
@Component
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
                                                ## Current context
                                                Current round: %s
                        
                                                ## Available tools (planning reference only)
                                                %s
                        
                                                ## Output format
                                                %s
                        """.formatted(overAllState.getRound(), toolDescriptions, converter.getFormat())),
                new UserMessage("""
                        【Research Topic】
                        %s
                        
                        【Conversation History】
                        %s
                        
                        ## Important constraints
                        If 【Critique Feedback】 exists in the conversation history, you must:
                        1. Analyze the gaps identified in the feedback
                        2. Ensure the new plan directly addresses those gaps
                        3. Do not repeat previously failed attempts
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
        DeepResearchStreams.emitThinking(sink, finished,
                "\n✅ Execution plan generated with " + planTasks.size() + " task(s)\n");

        if (!planTasks.isEmpty()) {
            var planText = new StringBuilder("\n📋 Execution plan:\n");
            for (var planTask : planTasks) {
                planText.append(String.format("  🟠 %s \n", planTask.instruction()));
            }
            DeepResearchStreams.emitThinking(sink, finished, planText.toString());
        }
        return planTasks;
    }
}
