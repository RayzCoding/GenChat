package com.genchat.application.agent;

import com.genchat.agent.*;
import com.genchat.application.strategy.PptStrategyDependencies;
import com.genchat.application.tool.FileContentTool;
import com.genchat.application.tool.GrepTool;
import com.genchat.application.tool.SkillsTool;
import com.genchat.common.utils.ToolMergeUtils;
import com.genchat.config.WebSearchToolInitConfig;
import com.genchat.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
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
    private final PptStrategyDependencies pptStrategyDependencies;

    @Value("${skills.directory:}")
    private String skillsDirectory;

    public WebSearchReactAgent createWebSearchAgent() {
        return new WebSearchReactAgent(
                chatModel,
                sessionService,
                agentTaskService,
                webSearchToolInitConfig.getWebSearchToolCallbacks(),
                5);
    }

    public DeepResearchAgent createDeepResearchAgent() {
        return new DeepResearchAgent(
                sessionService,
                chatModel,
                List.of(webSearchToolInitConfig.getWebSearchToolCallbacks()),
                agentTaskService,
                3);
    }

    public SimpleReactAgent createSimpleReactAgent() {
        return new SimpleReactAgent(
                chatModel,
                List.of(webSearchToolInitConfig.getWebSearchToolCallbacks()));
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
                pptStrategyDependencies);
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
        return new SkillsReactAgent(
                chatModel,
                sessionService,
                agentTaskService,
                toolCallbacks,
                5);
    }
}
