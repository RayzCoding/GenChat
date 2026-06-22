package com.genchat.common.prompts;

/**
 * React Agent prompts loaded from classpath templates.
 */
public final class ReactAgentPrompts {

    private ReactAgentPrompts() {
    }

    public static String getReactAgentSystemPrompt() {
        return PromptLoader.load("react/react-agent-system.md");
    }

    public static String getWebSearchPrompt() {
        return PromptLoader.format("react/web-search.md", java.time.LocalDateTime.now());
    }

    public static String getFilePrompt() {
        return PromptLoader.format("react/file.md", java.time.LocalDateTime.now());
    }

    public static String getSkillsPrompt() {
        return PromptLoader.format(
                "react/skills.md",
                java.time.LocalDateTime.now(),
                BaseAgentPrompts.TOOL_CALLING_RULES,
                BaseAgentPrompts.FINAL_ANSWER_RULES,
                BaseAgentPrompts.OUTPUT_SPECIFICATIONS,
                BaseAgentPrompts.MANDATORY_REQUIREMENTS
        );
    }

    public static String getWebSearchBasePrompt() {
        return PromptLoader.format(
                "react/web-search-base.md",
                java.time.LocalDateTime.now(),
                BaseAgentPrompts.TOOL_CALLING_RULES,
                BaseAgentPrompts.FINAL_ANSWER_RULES,
                BaseAgentPrompts.OUTPUT_SPECIFICATIONS,
                BaseAgentPrompts.MANDATORY_REQUIREMENTS
        );
    }

    public static String getFileBasePrompt() {
        return PromptLoader.format(
                "react/file-base.md",
                java.time.LocalDateTime.now(),
                BaseAgentPrompts.OUTPUT_SPECIFICATIONS,
                BaseAgentPrompts.FINAL_ANSWER_RULES,
                BaseAgentPrompts.MANDATORY_REQUIREMENTS
        );
    }

    public static String getRecommendPrompt() {
        return PromptLoader.format("react/recommend.md", java.time.LocalDateTime.now());
    }
}
