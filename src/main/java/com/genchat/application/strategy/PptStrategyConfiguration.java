package com.genchat.application.strategy;

import com.genchat.entity.PptInstStatus;
import com.genchat.service.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

@Configuration
public class PptStrategyConfiguration {

    @Bean
    public Map<PptInstStatus, PptStateStrategy> pptStateStrategyMap(
            RequirementStrategy requirementStrategy,
            TemplateStrategy templateStrategy,
            OutlineStrategy outlineStrategy,
            SearchStrategy searchStrategy,
            SchemaStrategy schemaStrategy,
            RenderStrategy renderStrategy,
            SuccessStrategy successStrategy,
            FailedStrategy failedStrategy) {
        var map = new EnumMap<PptInstStatus, PptStateStrategy>(PptInstStatus.class);
        map.put(PptInstStatus.INIT, requirementStrategy);
        map.put(PptInstStatus.REQUIREMENT, requirementStrategy);
        map.put(PptInstStatus.TEMPLATE, templateStrategy);
        map.put(PptInstStatus.OUTLINE, outlineStrategy);
        map.put(PptInstStatus.SEARCH, searchStrategy);
        map.put(PptInstStatus.SCHEMA, schemaStrategy);
        map.put(PptInstStatus.RENDER, renderStrategy);
        map.put(PptInstStatus.SUCCESS, successStrategy);
        map.put(PptInstStatus.FAILED, failedStrategy);
        return map;
    }

    @Bean
    public PptStrategyDependencies pptStrategyDependencies(
            AiPptInstService pptInstService,
            ChatModel chatModel,
            AgentTaskService agentTaskService,
            AiPptTemplateService pptTemplateService,
            AiChatSessionService sessionService,
            MinioService minioService,
            ImageGenerationService imageGenerationService,
            PptPythonRenderService pptPythonRenderService,
            PptStateStrategyFactory strategyFactory) {
        return new PptStrategyDependencies(
                pptInstService,
                chatModel,
                agentTaskService,
                pptTemplateService,
                sessionService,
                minioService,
                imageGenerationService,
                pptPythonRenderService,
                strategyFactory);
    }
}
