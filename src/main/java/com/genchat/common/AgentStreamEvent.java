package com.genchat.common;

import com.genchat.common.utils.JacksonJson;

/**
 * Agent stream event payloads serialized as SSE JSON lines.
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

    record Thinking(String content) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"thinking\",\"content\":" + escapeJson(content) + "}";
        }
    }

    record Text(String content) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"text\",\"content\":" + escapeJson(content) + "}";
        }
    }

    record ToolStart(String toolName, String toolCallId, String arguments) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"tool_start\",\"toolName\":" + escapeJson(toolName)
                    + ",\"toolCallId\":" + escapeJson(toolCallId)
                    + ",\"arguments\":" + escapeJson(arguments) + "}";
        }
    }

    record ToolEnd(String toolName, String toolCallId, String result) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"tool_end\",\"toolName\":" + escapeJson(toolName)
                    + ",\"toolCallId\":" + escapeJson(toolCallId)
                    + ",\"result\":" + escapeJson(result) + "}";
        }
    }

    record Reference(String content, Integer count) implements AgentStreamEvent {
        public static Reference of(String content) {
            return new Reference(content, JacksonJson.arraySize(content));
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

    record Recommend(String content, Integer count) implements AgentStreamEvent {
        public static Recommend of(String content) {
            return of(content, null);
        }

        public static Recommend of(String content, Integer count) {
            if (count == null) {
                count = JacksonJson.arraySize(content);
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

    record Complete() implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"complete\"}";
        }
    }

    String toJSON();

    static String serializeContent(String value) {
        return JacksonJson.serializeContent(value);
    }

    static String escapeJson(String value) {
        return JacksonJson.quoteJsonString(value);
    }
}
