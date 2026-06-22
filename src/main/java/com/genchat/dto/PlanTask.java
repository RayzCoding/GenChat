package com.genchat.dto;

/**
 * Planned task record.
 */
public record PlanTask(
        String id,
        String instruction,
        int order
) {
}
