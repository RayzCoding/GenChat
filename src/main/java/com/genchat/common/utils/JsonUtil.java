package com.genchat.common.utils;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonUtil {
    public static String getSafe(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
