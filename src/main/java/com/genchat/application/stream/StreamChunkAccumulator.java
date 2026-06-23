package com.genchat.application.stream;

import com.genchat.common.utils.JacksonJson;

/**
 * Accumulates plain-text answer and thinking content from SSE JSON chunks.
 */
public final class StreamChunkAccumulator {

    private StreamChunkAccumulator() {
    }

    public static void append(String chunk, StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer) {
        try {
            var json = JacksonJson.parseTreeLenient(chunk);
            if (json != null) {
                String type = json.path("type").asText(null);
                if ("text".equals(type)) {
                    finalAnswerBuffer.append(json.path("content").asText(""));
                } else if ("thinking".equals(type)) {
                    thinkingBuffer.append(json.path("content").asText(""));
                }
            } else {
                finalAnswerBuffer.append(chunk);
            }
        } catch (Exception e) {
            finalAnswerBuffer.append(chunk);
        }
    }
}
