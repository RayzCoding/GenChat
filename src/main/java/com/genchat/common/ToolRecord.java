package com.genchat.common;

/**
 *
 * @param toolName   Tool Name
 * @param toolCallId tool Call Id
 * @param arguments  arguments
 * @param result     result
 */
public record ToolRecord(
        String toolName,
        String toolCallId,
        String arguments,
        String result
) {
}
