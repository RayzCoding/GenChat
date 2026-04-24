package com.genchat.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * Agent unified response type
 * Used to standardize streaming output format across all agents
 */
public class AgentResponse {

    /**
     * Supported types
     */
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_THINKING = "thinking";
    public static final String TYPE_REFERENCE = "reference";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_RECOMMEND = "recommend";

    private String type;
    private String content;
    private Integer count;
    private Object data;

    public AgentResponse() {
    }

    public AgentResponse(String type, String content) {
        this.type = type;
        this.content = content;
    }

    public AgentResponse(String type, String content, Integer count) {
        this.type = type;
        this.content = content;
        this.count = count;
    }

    public AgentResponse(String type, String content, Integer count, Object data) {
        this.type = type;
        this.content = content;
        this.count = count;
        this.data = data;
    }

    // ===== Factory methods =====

    /**
     * Create a text type response
     */
    public static String text(String content) {
        return new AgentResponse(TYPE_TEXT, content).toJson();
    }

    /**
     * Create a thinking type response
     */
    public static String thinking(String content) {
        return new AgentResponse(TYPE_THINKING, content).toJson();
    }

    /**
     * Create a reference type response
     */
    public static String reference(String content, Integer count) {
        return new AgentResponse(TYPE_REFERENCE, content, count).toJson();
    }

    /**
     * Create a reference type response (without count, auto-parses JSON array to calculate count)
     */
    public static String reference(String content) {
        try {
            var jsonArray = JSON.parseArray(content);
            if (jsonArray != null) {
                return reference(content, jsonArray.size());
            }
        } catch (Exception e) {
            // Parse failed, count is null
        }
        return reference(content, null);
    }

    /**
     * Create an error type response
     */
    public static String error(String content) {
        return new AgentResponse(TYPE_ERROR, content).toJson();
    }

    /**
     * Create a recommend type response
     */
    public static String recommend(String content) {
        return recommend(content, null);
    }

    /**
     * Create a recommend type response (with count)
     */
    public static String recommend(String content, Integer count) {
        return new AgentResponse(TYPE_RECOMMEND, content, count).toJson();
    }

    /**
     * Create a JSON type response (custom type)
     */
    public static String json(String type, Object content) {
        if (TYPE_REFERENCE.equals(type) && content instanceof String jsonStr) {
            try {
                // Try to parse as JSONArray to calculate count
                var jsonArray = JSON.parseArray(jsonStr);
                if (jsonArray != null && !jsonArray.isEmpty()) {
                    return reference(jsonStr, jsonArray.size());
                }
            } catch (Exception e) {
                // Parse failed, use plain json response
            }
        }
        return new AgentResponse(type, content == null ? null : content.toString()).toJson();
    }

    // ===== JSON conversion =====

    public String toJson() {
        JSONObject obj = new JSONObject();
        obj.put("type", type);
        if (content != null) {
            obj.put("content", content);
        }
        if (count != null) {
            obj.put("count", count);
        }
        if (data != null) {
            if ((TYPE_REFERENCE.equals(type) || TYPE_RECOMMEND.equals(type)) && content != null) {
                try {
                    obj.put("content", JSON.parse(content));
                } catch (Exception e) {
                    obj.put("content", content);
                }
            } else {
                obj.put("data", data);
            }
        }
        return obj.toJSONString();
    }

    // ===== Getters and Setters =====

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
