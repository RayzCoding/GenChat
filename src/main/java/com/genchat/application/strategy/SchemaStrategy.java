package com.genchat.application.strategy;

import com.alibaba.fastjson.JSON;
import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.dto.FieldData;
import com.genchat.dto.PptSchema;
import com.genchat.dto.Slide;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;

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
                    context.continueStateMachine(inst, sink, question, thinkingBuffer);
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
        if (pptSchema.getSlides() == null) {
            return;
        }
        var imageGenerationTasks = new ArrayList<ImageGenerationTask>();
        pptSchema.getSlides().forEach(slide -> {
            if (slide.getData() == null) {
                return;
            }
            for (var entry : slide.getData().entrySet()) {
                var key = entry.getKey();
                var fieldData = entry.getValue();
                if (fieldData == null) {
                    continue;
                }
                var type = fieldData.getType();
                if (!"image".equalsIgnoreCase(type) && !"background".equalsIgnoreCase(type)) {
                    continue;
                }
                if (fieldData.getUrl() != null && !fieldData.getUrl().isEmpty()) {
                    continue;
                }
                var prompt = fieldData.getContent();
                if (prompt == null || prompt.isEmpty()) {
                    continue;
                }
                imageGenerationTasks.add(new ImageGenerationTask(key, fieldData, prompt, slide));
            }
            if (imageGenerationTasks.isEmpty()) {
                return;
            }
            var size = imageGenerationTasks.size();
            sink.tryEmitNext(AgentResponse.thinking("✅After the PPT content design is completed, start generating image materials\n"));
            sink.tryEmitNext(AgentResponse.thinking("A total of " + size + "images need to be generated, start generating...\n"));

            for (var i = 0; i < imageGenerationTasks.size(); i++) {
                var imageGenerationTask = imageGenerationTasks.get(i);
                int currentTask = i + 1;
                sink.tryEmitNext(AgentResponse.thinking("Image is being generated (" + currentTask + "/" + size + ")...\n"));
                try {
                    var originalImageUrl = context.getImageGenerationService().generateImage(imageGenerationTask.prompt);
                    var imageBytes = downloadImageFromUrl(originalImageUrl);
                    if (imageBytes != null) {
                        // upload to MinIO
                        String objectName = "ppt/" + conversationId + "/images/" + System.currentTimeMillis() + "_" + (i + 1) + ".png";
                        String minioUrl = context.getMinioService().uploadFile(objectName, imageBytes, "image/png");

                        // Update the URL in the schema to the MinIO address
                        imageGenerationTask.fieldData.setUrl(minioUrl);

                        sink.tryEmitNext(AgentResponse.thinking("✅ The image generation is complete (" + currentTask + "/" + size + ")...\n"));
                        log.info("Images have been uploaded to MinIO: {} -> {}", imageGenerationTask.key, minioUrl);
                    } else {
                        throw new RuntimeException("Could not download image from url: " + originalImageUrl);
                    }
                } catch (Exception e) {
                    log.error("Image generation or upload failed: {}", imageGenerationTask.prompt, e);
                    sink.tryEmitNext(AgentResponse.thinking("⚠ Image generation failed (" + currentTask + "/" + size + "): \n" + imageGenerationTask.key));
                    imageGenerationTask.fieldData.setUrl("");
                }
            }
        });

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
                    context.continueStateMachine(inst, sink, question, thinkingBuffer);
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

    private byte[] downloadImageFromUrl(String imageUrl) throws Exception {
        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        var request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("下载图片失败，状态码: " + response.statusCode());
        }
    }

    private static class ImageGenerationTask {
        String key;
        FieldData fieldData;
        String prompt;
        Slide slide;

        ImageGenerationTask(String key, FieldData fieldData, String prompt, Slide slide) {
            this.key = key;
            this.fieldData = fieldData;
            this.prompt = prompt;
            this.slide = slide;
        }
    }
}
