package com.genchat.context;

import java.util.HashSet;
import java.util.Set;

/**
 * Context compaction policy configuration.
 *
 * Controls compaction behavior in the agent loop, including token threshold,
 * how many recent tool rounds to keep, and protected tool names.
 */
public record ContextPolicy(
        int tokenThreshold,
        int keepRecentTools,
        int maxToolLength,
        int truncationKeepMessages,
        Set<String> protectedTools
) {

    /** Default token threshold */
    public static final int DEFAULT_TOKEN_THRESHOLD = 60000;
    /** Default number of recent tool-call rounds to keep */
    public static final int DEFAULT_KEEP_RECENT_TOOLS = 4;
    /** Default max tool content length before compaction (ToolResponse and ToolCall args) */
    public static final int DEFAULT_MAX_TOOL_LENGTH = 200;
    /** Default number of messages kept when LLM summary generation fails */
    public static final int DEFAULT_TRUNCATION_KEEP_MESSAGES = 10;
    /** Built-in protected tools */
    private static final Set<String> BUILTIN_PROTECTED_TOOLS = Set.of("Skill");

    public ContextPolicy {
        requireNonNegative(tokenThreshold, "tokenThreshold");
        requireNonNegative(keepRecentTools, "keepRecentTools");
        requireNonNegative(maxToolLength, "maxToolLength");
        requirePositive(truncationKeepMessages, "truncationKeepMessages");

        Set<String> allProtected = new HashSet<>(BUILTIN_PROTECTED_TOOLS);
        if (protectedTools != null) {
            allProtected.addAll(protectedTools);
        }
        protectedTools = Set.copyOf(allProtected);
    }

    public static ContextPolicy defaults() {
        return new ContextPolicy(
                DEFAULT_TOKEN_THRESHOLD,
                DEFAULT_KEEP_RECENT_TOOLS,
                DEFAULT_MAX_TOOL_LENGTH,
                DEFAULT_TRUNCATION_KEEP_MESSAGES,
                Set.of()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isProtected(String toolName) {
        return protectedTools.contains(toolName);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, got " + value);
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0, got " + value);
        }
    }

    public static class Builder {
        private int tokenThreshold = DEFAULT_TOKEN_THRESHOLD;
        private int keepRecentTools = DEFAULT_KEEP_RECENT_TOOLS;
        private int maxToolLength = DEFAULT_MAX_TOOL_LENGTH;
        private int truncationKeepMessages = DEFAULT_TRUNCATION_KEEP_MESSAGES;
        private Set<String> protectedTools = Set.of();

        public Builder tokenThreshold(int v) {
            this.tokenThreshold = v;
            return this;
        }

        public Builder keepRecentTools(int v) {
            this.keepRecentTools = v;
            return this;
        }

        public Builder maxToolLength(int v) {
            this.maxToolLength = v;
            return this;
        }

        public Builder truncationKeepMessages(int v) {
            this.truncationKeepMessages = v;
            return this;
        }

        public Builder protectedTools(String... tools) {
            this.protectedTools = Set.of(tools);
            return this;
        }

        public ContextPolicy build() {
            return new ContextPolicy(
                    tokenThreshold,
                    keepRecentTools,
                    maxToolLength,
                    truncationKeepMessages,
                    protectedTools
            );
        }
    }
}
