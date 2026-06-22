package com.genchat.dto;

/**
 * Task execution result.
 */
public record TaskResult(
        String taskId,
        boolean success,
        String output,
        String error
) {
}
