package com.genchat.application.agent;

import com.genchat.agent.*;
import com.genchat.application.strategy.PptStrategyDependencies;
import com.genchat.application.tool.FileContentTool;
import com.genchat.application.tool.GrepTool;
import com.genchat.application.tool.SkillsTool;
import com.genchat.common.utils.ToolMergeUtils;
import com.genchat.config.WebSearchToolInitConfig;
import com.genchat.config.GenChatProperties;
import com.genchat.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentFactory {

    private final ChatModel chatModel;
    private final AiChatSessionService sessionService;
    private final AgentTaskService agentTaskService;
    private final WebSearchToolInitConfig webSearchToolInitConfig;
    private final FileContentTool fileContentTool;
    private final ObjectProvider<PptStrategyDependencies> pptStrategyDependenciesProvider;
    private final ObjectProvider<DeepResearchAgent> deepResearchAgentProvider;
    private final GenChatProperties genChatProperties;

    @Value("${skills.directory:}")
    private String skillsDirectory;

    public WebSearchReactAgent createWebSearchAgent() {
        var agent = new WebSearchReactAgent(
                chatModel,
                sessionService,
                agentTaskService,
                webSearchToolInitConfig.getWebSearchToolCallbacks(),
                genChatProperties.getAgent().getMaxRounds());
        agent.setMaxRetries(genChatProperties.getAgent().getMaxRetries());
        return agent;
    }

    public DeepResearchAgent createDeepResearchAgent() {
        return deepResearchAgentProvider.getObject();
    }

    public SimpleReactAgent createSimpleReactAgent() {
        return createSimpleReactAgent(List.of(webSearchToolInitConfig.getWebSearchToolCallbacks()));
    }

    public SimpleReactAgent createSimpleReactAgent(List<ToolCallback> tools) {
        return new SimpleReactAgent(chatModel, tools);
    }

    public FileReactAgent createFileReactAgent() {
        return new FileReactAgent(
                chatModel,
                sessionService,
                agentTaskService,
                List.of(ToolCallbacks.from(fileContentTool)));
    }

    public PPTBuilderAgent createPptBuilderAgent() {
        return new PPTBuilderAgent(
                chatModel,
                sessionService,
                agentTaskService,
                webSearchToolInitConfig.getWebSearchToolCallbacks(),
                pptStrategyDependenciesProvider.getObject());
    }

    public SkillsReactAgent createSkillsReactAgent() {
        var webSearchToolCallbacks = webSearchToolInitConfig.getWebSearchToolCallbacks();
        var toolCallbacks = ToolMergeUtils.mergeTools(
                webSearchToolCallbacks,
                ToolCallbacks.from(fileContentTool),
                new ToolCallback[]{SkillsTool.builder()
                        .addSkillsDirectory(skillsDirectory)
                        .build()},
                GrepTool.create());
        var agent = new SkillsReactAgent(
                chatModel,
                sessionService,
                agentTaskService,
                toolCallbacks,
                genChatProperties.getAgent().getMaxRounds());
        agent.setMaxRetries(genChatProperties.getAgent().getMaxRetries());
        return agent;
    }
}
