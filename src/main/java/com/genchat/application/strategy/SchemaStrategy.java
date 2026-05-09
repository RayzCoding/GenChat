package com.genchat.application.strategy;

import com.alibaba.fastjson.JSON;
import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.dto.PptSchema;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class SchemaStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.RENDER;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        sink.tryEmitNext(AgentResponse.thinking("PPT details are being designed....\n"));

        var templateCode = inst.getTemplateCode();
        var aiPptTemplate = context.getPptTemplateService().getByTemplateCode(templateCode);
        var pptTemplate = aiPptTemplate.get();
        var templateSchema = pptTemplate.getTemplateSchema();
        var outline = inst.getOutline();
        var schemaGenerationPrompt = PptBuilderPrompts.getSchemaGenerationPrompt(templateSchema, outline);
        var pptSchemaBeanOutputConverter = new BeanOutputConverter<PptSchema>(new ParameterizedTypeReference<>() {
        });

        var disposable = Mono.fromCallable(() -> {
                    var text = context.getChatModel().call(new Prompt(schemaGenerationPrompt)).getResult().getOutput().getText();
                    var pptSchema = pptSchemaBeanOutputConverter.convert(text);
                    var pptSchemaJson = JSON.toJSONString(pptSchema);

                    inst.setPptSchema(pptSchemaJson);
                    inst.setStatus(TARGET_STATUS.getCode());
                    context.getPptInstService().updateInst(inst);

                    processImageGeneration(pptSchema, sink, inst.getConversationId(), context);
                    inst.setPptSchema(JSON.toJSONString(pptSchema));
                    context.getPptInstService().updateInst(inst);
                    context.continueStateMachine(inst,sink, question, thinkingBuffer);
                    return null;
                })
                .doOnError(throwable -> {
                    log.error("Schema generation failed", throwable);
                    inst.setStatus(PptInstStatus.SCHEMA.getCode());
                    context.getPptInstService().updateInst(inst);
                    PptStateStrategyFactory.getInstance().executeFailedStrategy(inst, sink, question, thinkingBuffer, context);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        context.setDisposable(inst.getConversationId(), disposable);
    }

    private void processImageGeneration(PptSchema pptSchema,
                                        Sinks.Many<String> sink,
                                        String conversationId,
                                        PptStateStrategyContext context) {

    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }

    public void executeWithModifyPrompt(AiPptInst inst,
                                        Sinks.Many<String> sink,
                                        String question,
                                        StringBuilder thinkingBuffer,
                                        PptStateStrategyContext context,
                                        String schemaModifyPrompt) {
        sink.tryEmitNext(AgentResponse.thinking("The PPT details are being regenerated....\n"));

        var pptSchemaBeanOutputConverter = new BeanOutputConverter<PptSchema>(new ParameterizedTypeReference<>() {
        });

        var disposable = Mono.fromCallable(() -> {
                    var text = context.getChatModel().call(new Prompt(schemaModifyPrompt))
                            .getResult()
                            .getOutput()
                            .getText();
                    var pptSchema = pptSchemaBeanOutputConverter.convert(text);
                    var pptSchemaJson = JSON.toJSONString(pptSchema);

                    inst.setPptSchema(pptSchemaJson);
                    inst.setStatus(TARGET_STATUS.getCode());
                    context.getPptInstService().updateInst(inst);

                    processImageGeneration(pptSchema, sink, inst.getConversationId(), context);
                    inst.setPptSchema(JSON.toJSONString(pptSchema));
                    context.getPptInstService().updateInst(inst);
                    context.continueStateMachine(inst,sink, question, thinkingBuffer);
                    return null;
                })
                .doOnError(throwable -> {
                    log.error("Schema generation failed", throwable);
                    inst.setStatus(PptInstStatus.SCHEMA.getCode());
                    context.getPptInstService().updateInst(inst);
                    PptStateStrategyFactory.getInstance().executeFailedStrategy(inst, sink, question, thinkingBuffer, context);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        context.setDisposable(inst.getConversationId(), disposable);
    }
}
