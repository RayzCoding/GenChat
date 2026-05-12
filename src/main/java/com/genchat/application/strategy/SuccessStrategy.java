package com.genchat.application.strategy;

import com.alibaba.fastjson2.JSON;
import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.dto.PptSchema;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class SuccessStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.SUCCESS;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        var fileUrl = inst.getFileUrl();
        var pptSchema = JSON.parseObject(inst.getPptSchema(), PptSchema.class);
        var size = pptSchema.getSlides().size();
        String prompt;

        if (context.isModifyMode()) {
            var modifyQuestion = context.getModifyQuestion();
            prompt = PptBuilderPrompts.getModifySummaryPrompt(modifyQuestion, fileUrl);
        } else {
            var requirement = inst.getRequirement();
            prompt = PptBuilderPrompts.getSummaryPrompt(requirement, fileUrl, size);
        }
        var llmResponse = new StringBuilder();
        var disposable = context.getChatClient().prompt()
                .messages(new UserMessage(prompt))
                .stream()
                .content()
                .doOnNext(chunk -> {
                    sink.tryEmitNext(AgentResponse.text(chunk));
                    llmResponse.append(chunk);
                })
                .doOnComplete(() -> {
                    updateSession(thinkingBuffer, context, llmResponse);
                    sink.tryEmitComplete();
                    context.setModifyMode(false);
                    context.setModifyQuestion(null);
                })
                .doOnError(err -> {
                    log.info("Summary generation failed", err);
                    sink.tryEmitError(err);
                    context.setModifyMode(false);
                    context.setModifyQuestion(null);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        context.setDisposable(inst.getConversationId(), disposable);
    }

    private static void updateSession(StringBuilder thinkingBuffer, PptStateStrategyContext context, StringBuilder llmResponse) {
        var aiChatSessionOptional = context.getSessionService().queryById(context.getCurrentSessionId());
        if (aiChatSessionOptional.isEmpty()) {
            log.error("Session id {} not found", context.getCurrentSessionId());
            return;
        }
        var aiChatSession = aiChatSessionOptional.get();
        aiChatSession.setAnswer(llmResponse.toString());
        aiChatSession.setThinking(thinkingBuffer.toString());
        context.getSessionService().updateSession(aiChatSession);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
