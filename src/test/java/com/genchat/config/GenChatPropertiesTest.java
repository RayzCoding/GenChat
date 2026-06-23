package com.genchat.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenChatPropertiesTest {

    @Test
    void defaultValuesMatchPreviousHardcodedConstants() {
        var props = new GenChatProperties();

        assertEquals(5, props.getAgent().getMaxRounds());
        assertEquals(0, props.getAgent().getMaxRetries());
        assertEquals(30, props.getAgent().getChatMemorySize());
        assertEquals(500, props.getFile().getChunkSize());
        assertEquals(50, props.getFile().getChunkOverlap());
        assertEquals(3, props.getDeepResearch().getMaxRounds());
        assertEquals(3, props.getDeepResearch().getToolSemaphorePermits());
    }
}
