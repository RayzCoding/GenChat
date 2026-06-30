package com.genchat.application.validation;

import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * Validates agent stream HTTP request parameters.
 */
public final class StreamRequestValidator {

    private StreamRequestValidator() {
    }

    /**
     * Returns a failed flux when validation fails; {@code null} means the request is valid.
     */
    public static Flux<String> requireQuestionFlux(String question) {
        if (!StringUtils.hasLength(question)) {
            return Flux.error(new IllegalArgumentException("question is null or empty"));
        }
        return null;
    }

    /**
     * Returns a failed flux when validation fails; {@code null} means the request is valid.
     */
    public static Flux<String> requireConversationAndQuestionFlux(String conversationId, String question) {
        if (ObjectUtils.isEmpty(conversationId) || ObjectUtils.isEmpty(question)) {
            return Flux.error(new IllegalArgumentException("conversationId or question is null or empty"));
        }
        return null;
    }

    /**
     * Returns a failed flux when validation fails; {@code null} means the request is valid.
     */
    public static Flux<String> requireConversationIdFlux(String conversationId) {
        if (ObjectUtils.isEmpty(conversationId)) {
            return Flux.error(new IllegalArgumentException("conversationsId is null or empty"));
        }
        return null;
    }

    /**
     * Returns a failed flux when validation fails; {@code null} means the request is valid.
     */
    public static Flux<String> requireFileStreamParamsFlux(String conversationId, String question, String fileId) {
        var questionError = requireQuestionFlux(question);
        if (questionError != null) {
            return questionError;
        }
        if (ObjectUtils.isEmpty(conversationId) || ObjectUtils.isEmpty(fileId)) {
            return Flux.error(new IllegalArgumentException("conversationId or question is null or empty"));
        }
        return null;
    }
}
