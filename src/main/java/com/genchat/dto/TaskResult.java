package com.genchat.dto;

/**
 * 任务执行结果
 */
public record TaskResult(
        String taskId,
        boolean success,
        String output,
        String error
) {
}
