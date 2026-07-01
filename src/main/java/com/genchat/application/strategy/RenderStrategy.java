package com.genchat.application.strategy;

import com.genchat.common.AgentStreamEvent;
import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class RenderStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.SUCCESS;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        var pptInstService = context.getDependencies().pptInstService();
        if (StringUtils.hasText(inst.getFileUrl())) {
            if (!TARGET_STATUS.getCode().equals(inst.getStatus())) {
                inst.setStatus(TARGET_STATUS.getCode());
                pptInstService.updateInst(inst);
            }
            context.continueStateMachine(inst, sink, question, thinkingBuffer);
            return;
        }

        sink.tryEmitNext(new AgentStreamEvent.Thinking("Rendering PPT...\n").toJSON());
        var disposable = Mono.fromCallable(() -> {
                    var pptSchemaJson = inst.getPptSchema();
                    return context.getDependencies().pptPythonRenderService().renderPpt(inst, pptSchemaJson);
                })
                .doOnSuccess(fileUrl -> {
                    inst.setFileUrl(fileUrl);
                    inst.setStatus(TARGET_STATUS.getCode());
                    pptInstService.updateInst(inst);
                    sink.tryEmitNext(new AgentStreamEvent.Thinking("✅Rendering PPT Completed...\n").toJSON());
                    context.continueStateMachine(inst, sink, question, thinkingBuffer);
                })
                .doOnError(throwable -> {
                    log.error("Rendering PPT Failed", throwable);
                    inst.setStatus(PptInstStatus.FAILED.getCode());
                    inst.setErrorMsg(throwable.getMessage());
                    pptInstService.updateInst(inst);
                    context.getDependencies().strategyFactory().executeFailedStrategy(inst, sink, question, thinkingBuffer, context);
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
