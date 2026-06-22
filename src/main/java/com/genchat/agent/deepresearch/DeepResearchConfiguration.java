package com.genchat.agent.deepresearch;

import com.genchat.agent.DeepResearchAgent;
import com.genchat.config.WebSearchToolInitConfig;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;

@Configuration
public class DeepResearchConfiguration {

    @Bean
    public DeepResearchDependencies deepResearchDependencies(
            AiChatSessionService sessionService,
            ChatModel chatModel,
            WebSearchToolInitConfig webSearchToolInitConfig,
            AgentTaskService agentTaskService) {
        return new DeepResearchDependencies(
                sessionService,
                chatModel,
                List.of(webSearchToolInitConfig.getWebSearchToolCallbacks()),
                agentTaskService,
                3);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public DeepResearchAgent deepResearchAgent(
            DeepResearchDependencies deps,
            DeepResearchPreparation preparation,
            DeepResearchPlanLoop planLoop) {
        return new DeepResearchAgent(deps, preparation, planLoop);
    }
}
