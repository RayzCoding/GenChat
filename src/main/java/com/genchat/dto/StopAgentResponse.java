package com.genchat.dto;

/**
 * Response body for {@code GET /agent/stop}.
 */
public record StopAgentResponse(
        boolean success,
        String message
) {
}
