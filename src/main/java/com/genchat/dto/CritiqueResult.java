package com.genchat.dto;

/**
 * Critique result record.
 */
public record CritiqueResult(
        boolean passed,
        String feedback
) {
}
