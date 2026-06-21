package com.genchat.agent.core;

/**
 * Parameters for a ReAct agent streaming session.
 */
public record ReactStreamRequest(
        String conversationId,
        String question,
        String fileId
) {
    public static ReactStreamRequest of(String conversationId, String question) {
        return new ReactStreamRequest(conversationId, question, null);
    }

    public static ReactStreamRequest withFile(String conversationId, String question, String fileId) {
        return new ReactStreamRequest(conversationId, question, fileId);
    }
}
