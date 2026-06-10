package com.genchat.common;

import com.alibaba.fastjson.JSON;

/**
 * Agent Stream Incident
 *
 */
public sealed interface AgentStreamEvent permits
        AgentStreamEvent.Thinking,
        AgentStreamEvent.Text,
        AgentStreamEvent.ToolStart,
        AgentStreamEvent.ToolEnd,
        AgentStreamEvent.Reference,
        AgentStreamEvent.Recommend,
        AgentStreamEvent.Error,
        AgentStreamEvent.Complete {

    /**
     * LLM thinking
     */
    record Thinking(String content) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"thinking\",\"content\":" + escapeJson(content) + "}";
        }
    }

    /**
     * LLM text output。
     */
    record Text(String content) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"text\",\"content\":" + escapeJson(content) + "}";
        }
    }

    /**
     * start execute tool。
     */
    record ToolStart(String toolName, String toolCallId, String arguments) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"tool_start\",\"toolName\":" + escapeJson(toolName)
                    + ",\"toolCallId\":" + escapeJson(toolCallId)
                    + ",\"arguments\":" + escapeJson(arguments) + "}";
        }
    }

    /**
     * completed execute tool
     */
    record ToolEnd(String toolName, String toolCallId, String result) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"tool_end\",\"toolName\":" + escapeJson(toolName)
                    + ",\"toolCallId\":" + escapeJson(toolCallId)
                    + ",\"result\":" + escapeJson(result) + "}";
        }
    }

    /**
     * reference sources。
     */
    record Reference(String content, Integer count) implements AgentStreamEvent {
        public static Reference of(String content) {
            Integer count = null;
            try {
                var jsonArray = JSON.parseArray(content);
                if (jsonArray != null) {
                    count = jsonArray.size();
                }
            } catch (Exception ignored) {
                // count remains null
            }
            return new Reference(content, count);
        }

        @Override
        public String toJSON() {
            StringBuilder sb = new StringBuilder("{\"type\":\"reference\",\"content\":");
            sb.append(serializeContent(content));
            if (count != null) {
                sb.append(",\"count\":").append(count);
            }
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * recommended follow-up questions。
     */
    record Recommend(String content, Integer count) implements AgentStreamEvent {
        public static Recommend of(String content) {
            return of(content, null);
        }

        public static Recommend of(String content, Integer count) {
            if (count == null) {
                try {
                    var jsonArray = JSON.parseArray(content);
                    if (jsonArray != null) {
                        count = jsonArray.size();
                    }
                } catch (Exception ignored) {
                    // count remains null
                }
            }
            return new Recommend(content, count);
        }

        @Override
        public String toJSON() {
            StringBuilder sb = new StringBuilder("{\"type\":\"recommend\",\"content\":");
            sb.append(serializeContent(content));
            if (count != null) {
                sb.append(",\"count\":").append(count);
            }
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * error event。
     */
    record Error(String code, String message, String detail) implements AgentStreamEvent {
        public static Error simple(String message) {
            return new Error("ERROR", message, null);
        }

        @Override
        public String toJSON() {
            return "{\"type\":\"error\",\"code\":" + escapeJson(code)
                    + ",\"message\":" + escapeJson(message)
                    + ",\"content\":" + escapeJson(message)
                    + ",\"detail\":" + escapeJson(detail) + "}";
        }
    }

    /**
     * Agent execute complete。
     */
    record Complete() implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"complete\"}";
        }
    }

    /**
     * Serialize to a JSON string
     */
    String toJSON();

    /**
     * Serialize reference/recommend content: parse JSON when possible, otherwise escape as string.
     */
    static String serializeContent(String value) {
        if (value == null) {
            return "null";
        }
        try {
            return JSON.toJSONString(JSON.parse(value));
        } catch (Exception e) {
            return escapeJson(value);
        }
    }

    /**
     * JSON String escape
     */
    static String escapeJson(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
