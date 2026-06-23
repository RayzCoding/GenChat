package com.genchat.application.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StreamRequestValidatorTest {

    @Test
    void resolveConversationIdPrefersConversationIdParam() {
        assertEquals("primary",
                StreamRequestValidator.resolveConversationId("primary", "legacy"));
    }

    @Test
    void resolveConversationIdFallsBackToConversationsId() {
        assertEquals("legacy",
                StreamRequestValidator.resolveConversationId(null, "legacy"));
    }

    @Test
    void requireQuestionFluxReturnsErrorWhenEmpty() {
        assertNotNull(StreamRequestValidator.requireQuestionFlux(""));
    }

    @Test
    void requireQuestionFluxReturnsNullWhenValid() {
        assertNull(StreamRequestValidator.requireQuestionFlux("hello"));
    }
}
