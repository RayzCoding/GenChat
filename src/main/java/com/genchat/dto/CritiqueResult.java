package com.genchat.dto;

/**
 * 批评结果记录
 */
public record CritiqueResult(
        boolean passed,
        String feedback
) {
}
