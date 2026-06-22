package com.genchat.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Shared Jackson helpers for JSON serialization and parsing.
 */
public final class JacksonJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JacksonJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize JSON", e);
        }
    }

    public static JsonNode parseTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse JSON", e);
        }
    }

    public static JsonNode parseTreeLenient(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static Integer arraySize(String jsonArray) {
        JsonNode node = parseTreeLenient(jsonArray);
        if (node != null && node.isArray()) {
            return node.size();
        }
        return null;
    }

    public static String textField(String json, String field) {
        JsonNode node = parseTreeLenient(json);
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public static String serializeContent(String value) {
        if (value == null) {
            return "null";
        }
        JsonNode node = parseTreeLenient(value);
        if (node != null) {
            return toJson(node);
        }
        return quoteJsonString(value);
    }

    public static String stopMessageJson(String content) {
        ObjectNode obj = MAPPER.createObjectNode();
        obj.put("type", "text");
        obj.put("content", content);
        return toJson(obj);
    }

    public static String quoteJsonString(String value) {
        if (value == null) {
            return "null";
        }
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
