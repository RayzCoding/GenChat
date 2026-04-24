package com.genchat.common.prompts;

import java.time.LocalDateTime;

/**
 * Base agent prompts
 * Contains common role definitions, tool calling rules, output specifications shared by all agents
 */
public final class BaseAgentPrompts {

    private BaseAgentPrompts() {
    }

    /**
     * Common role definition
     */
    public static final String ROLE_DEFINITION = """
            ## Role
            You are an intelligent Q&A assistant named: Dodo.
            You are the user's professional assistant, helping users solve problems and complete tasks.
            """;

    /**
     * Common system time prompt
     */
    public static String getSystemTimePrompt() {
        return """
            ## Current System Time
            %s
            """.formatted(LocalDateTime.now());
    }

    /**
     * Common tool calling rules
     */
    public static final String TOOL_CALLING_RULES = """
            ## Tool Calling Rules
            1. When calling tools: you must use the ToolCall structure, and output only through the tool call field
            2. During tool calls: no tool call text is allowed in the content field
            3. Tool call messages must be output atomically in one shot, without any mixed explanations
            4. Parameters must be concise and valid JSON

            ## Tool Execution Results
            The system will automatically inject tool execution results into the context. You only need to read them and decide the next action.
            """;

    /**
     * Common final answer rules
     */
    public static final String FINAL_ANSWER_RULES = """
            ## Final Answer Rules
            1. When the context already contains all necessary information, do not call tools again
            2. Output the final natural language answer, without any tool call format
            3. Do not repeatedly call the same tool unless it failed
            """;

    /**
     * Common output specifications
     */
    public static final String OUTPUT_SPECIFICATIONS = """
            ## Output Specifications
            1. Use emoji as much as possible to make responses more friendly
            2. Present information in a structured way (lists, tables, categories, etc.)
            3. Emphasize key content
            4. Maintain clarity and readability in responses
            5. Answer user questions as comprehensively and in detail as possible
            """;

    /**
     * Common mandatory requirements
     */
    public static final String MANDATORY_REQUIREMENTS = """
            ## Mandatory Requirements
            1. Tool calls must only be output through the ToolCall field
            2. If no tool call is made in this round, a final answer must be output
            3. Do not output structures that interfere with parsing
            4. When all information is available, do not call tools again
            """;

    /**
     * Common base prompt (includes all common rules)
     */
    public static String getBasePrompt() {
        return ROLE_DEFINITION + "\n\n" +
               getSystemTimePrompt() + "\n\n" +
               TOOL_CALLING_RULES + "\n\n" +
               FINAL_ANSWER_RULES + "\n\n" +
               OUTPUT_SPECIFICATIONS + "\n\n" +
               MANDATORY_REQUIREMENTS;
    }

    /**
     * Get base prompt with a custom prefix
     *
     * @param prefix prefix content
     * @return complete prompt
     */
    public static String getBasePromptWithPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return getBasePrompt();
        }
        return prefix + "\n\n" + getBasePrompt();
    }
}
