package com.genchat.application.strategy;

import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;

@Slf4j
public class RequirementStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.SEARCH;


    @Override
    public void execute(AiPptInst inst, Sinks.Many<String> sink, String question,
                        StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        sink.tryEmitNext(AgentResponse.thinking("Your needs are being analyzed...\n"));
        var messages = new ArrayList<Message>();
        var requirementPrompt = PptBuilderPrompts.REQUIREMENT_PROMPT;
        messages.add(new SystemMessage(requirementPrompt));

        var conversationId = inst.getConversationId();
        context.loadChatHistory(conversationId, messages, true, true);
        messages.add(new UserMessage("<question>" + question + "</question>"));

        if (context.getChatMemory() != null) {
            context.getChatMemory().add(conversationId, new UserMessage(question));
        }

        var responseBuffer = new StringBuilder();
        var disposable = context.getChatClient().prompt()
                .messages(messages)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    responseBuffer.append(chunk);
                    sink.tryEmitNext(AgentResponse.thinking(chunk));
                })
                .doOnComplete(() -> {
                    log.info("Requirements analysis completed: {}", responseBuffer);
                    var response = responseBuffer.toString();
                    if (context.shouldContinueToNextStep(response)) {
                        // The information is complete, continue to the next step: information collection
                        inst.setRequirement(response);
                        inst.setStatus(TARGET_STATUS.getCode());
                        context.getPptInstService().updateInst(inst);
                        sink.tryEmitNext(AgentResponse.thinking("\n✅ The requirements are confirmed and the relevant information is being collected\n"));
                        context.continueStateMachine(inst, sink, question, thinkingBuffer);
                    }else {
                        // If the information is insufficient, save the current state and go to the unified output of the FAILED policy
                        inst.setRequirement(response);
                        inst.setStatus(PptInstStatus.REQUIREMENT.getCode());
                        inst.setErrorMsg(response);
                        context.getPptInstService().updateInst(inst);
                        if (context.getChatMemory() != null) {
                            context.getChatMemory().add(conversationId, new AssistantMessage(response));
                        }
                        // Go to FAILED policy
                        PptStateStrategyFactory.getInstance().executeFailedStrategy(inst,sink, question,thinkingBuffer,context);
                    }

                })
                .doOnError(throwable -> {
                    log.error("Requirements analysis anomalies", throwable);
                    // When it fails, it does not fall back to the state, only updates the error message, and goes to FAILED
                    inst.setErrorMsg(throwable.getMessage());
                    inst.setStatus(PptInstStatus.FAILED.getCode());
                    context.getPptInstService().updateInst(inst);
                    // Go to FAILED policy
                    PptStateStrategyFactory.getInstance().executeFailedStrategy(inst, sink, question, thinkingBuffer, context);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        // Save the disposable to the task manager to stop the task
        context.setDisposable(conversationId, disposable);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
