package com.genchat.application.strategy;

import com.genchat.common.AgentResponse;
import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class RenderStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.SUCCESS;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        sink.tryEmitNext(AgentResponse.thinking("Rendering PPT...\n"));
        var disposable = Mono.fromCallable(() -> {
                    var pptSchemaJson = inst.getPptSchema();
                    return context.getPptPythonRenderService().renderPpt(inst,pptSchemaJson);
                })
                .doOnSuccess(fileUrl -> {
                    inst.setFileUrl(fileUrl);
                    inst.setStatus(TARGET_STATUS.getCode());
                    sink.tryEmitNext(AgentResponse.thinking("✅Rendering PPT Completed...\n"));
                    context.continueStateMachine(inst, sink, question, thinkingBuffer);
                })
                .doOnError(throwable -> {
                    log.error("Rendering PPT Failed", throwable);
                    inst.setStatus(PptInstStatus.RENDER.getCode());
                    inst.setErrorMsg(throwable.getMessage());
                    PptStateStrategyFactory.getInstance().executeFailedStrategy(inst,sink,question,thinkingBuffer,context);
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
