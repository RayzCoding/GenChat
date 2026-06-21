package com.genchat.application.strategy;

import com.genchat.common.AgentStreamEvent;
import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Component;
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
        sink.tryEmitNext(new AgentStreamEvent.Thinking("Rendering PPT...\n").toJSON());
        var disposable = Mono.fromCallable(() -> {
                    var pptSchemaJson = inst.getPptSchema();
                    return context.getPptPythonRenderService().renderPpt(inst, pptSchemaJson);
                })
                .doOnSuccess(fileUrl -> {
                    inst.setFileUrl(fileUrl);
                    inst.setStatus(TARGET_STATUS.getCode());
                    sink.tryEmitNext(new AgentStreamEvent.Thinking("✅Rendering PPT Completed...\n").toJSON());
                    context.continueStateMachine(inst, sink, question, thinkingBuffer);
                })
                .doOnError(throwable -> {
                    log.error("Rendering PPT Failed", throwable);
                    inst.setStatus(PptInstStatus.RENDER.getCode());
                    inst.setErrorMsg(throwable.getMessage());
                    context.getStrategyFactory().executeFailedStrategy(inst, sink, question, thinkingBuffer, context);
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
