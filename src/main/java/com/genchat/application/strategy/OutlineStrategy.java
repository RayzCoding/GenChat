package com.genchat.application.strategy;

import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class OutlineStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.SEARCH;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        // loading template by code
        var templateCode = inst.getTemplateCode();
        var templateOptional = context.getPptTemplateService().getByTemplateCode(templateCode);
        if (templateOptional.isEmpty()) {
            log.info("Template not found for templateCode {}", templateCode);
            inst.setErrorMsg("Template not found for templateCode: " + templateCode);
            inst.setStatus(PptInstStatus.TEMPLATE.getCode());
            context.getPptInstService().updateInst(inst);
            PptStateStrategyFactory.getInstance().executeFailedStrategy(inst,
                    sink, question, thinkingBuffer, context);
            return;
        }
        // Generate an outline based on the template's schema and search information
        var pptTemplate = templateOptional.get();
        var outlinePrompt = PptBuilderPrompts.getOutlinePrompt(inst.getRequirement(), pptTemplate.getTemplateSchema(),
                pptTemplate.getTemplateName(), inst.getSearchInfo());
        var outlineContent = new StringBuilder();
        var disposable = context.getChatClient().prompt()
                .messages(new UserMessage(outlinePrompt))
                .stream()
                .content()
                .doOnNext(chunk -> {
                    sink.tryEmitNext(AgentResponse.thinking(chunk));
                    outlineContent.append(chunk);
                })
                .doOnComplete(() -> {
                    log.info("Outline generate completed.");
                    inst.setStatus(TARGET_STATUS.getCode());
                    inst.setOutline(outlineContent.toString());
                    context.getPptInstService().updateInst(inst);
                    sink.tryEmitNext(AgentResponse.thinking("\n ✅ After the outline is generated, start designing the PPT details\n"));
                    context.continueStateMachine(inst, sink, question, thinkingBuffer);
                })
                .doOnError(err -> {
                    log.info("Outline generate failed.", err);
                    inst.setStatus(PptInstStatus.OUTLINE.getCode());
                    inst.setErrorMsg("Outline generate failed: " + err.getMessage());
                    context.getPptInstService().updateInst(inst);
                    PptStateStrategyFactory.getInstance().executeFailedStrategy(inst, sink, question, thinkingBuffer, context);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        context.setDisposable(inst.getConversationId(), disposable);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
