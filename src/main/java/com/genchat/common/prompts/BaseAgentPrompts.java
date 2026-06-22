package com.genchat.common.prompts;

import java.time.LocalDateTime;

/**
 * Base agent prompts loaded from classpath templates.
 */
public final class BaseAgentPrompts {

    private BaseAgentPrompts() {
    }

    public static final String ROLE_DEFINITION = PromptLoader.load("base/role-definition.md");
    public static final String TOOL_CALLING_RULES = PromptLoader.load("base/tool-calling-rules.md");
    public static final String FINAL_ANSWER_RULES = PromptLoader.load("base/final-answer-rules.md");
    public static final String OUTPUT_SPECIFICATIONS = PromptLoader.load("base/output-specifications.md");
    public static final String MANDATORY_REQUIREMENTS = PromptLoader.load("base/mandatory-requirements.md");

    public static String getSystemTimePrompt() {
        return PromptLoader.format("base/system-time.md", LocalDateTime.now());
    }

    public static String getBasePrompt() {
        return ROLE_DEFINITION + "\n\n" +
               getSystemTimePrompt() + "\n\n" +
               TOOL_CALLING_RULES + "\n\n" +
               FINAL_ANSWER_RULES + "\n\n" +
               OUTPUT_SPECIFICATIONS + "\n\n" +
               MANDATORY_REQUIREMENTS;
    }

    public static String getBasePromptWithPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return getBasePrompt();
        }
        return prefix + "\n\n" + getBasePrompt();
    }
}
