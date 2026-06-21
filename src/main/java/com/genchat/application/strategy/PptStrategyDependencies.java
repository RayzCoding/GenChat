package com.genchat.application.strategy;

import com.genchat.service.*;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Shared service dependencies for PPT state machine execution.
 */
public record PptStrategyDependencies(
        AiPptInstService pptInstService,
        ChatModel chatModel,
        AgentTaskService agentTaskService,
        AiPptTemplateService pptTemplateService,
        AiChatSessionService sessionService,
        MinioService minioService,
        ImageGenerationService imageGenerationService,
        PptPythonRenderService pptPythonRenderService,
        PptStateStrategyFactory strategyFactory
) {
}
