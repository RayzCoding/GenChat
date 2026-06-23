package com.genchat.agent.core;

import com.genchat.entity.RoundState;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactChunkProcessorTest {

    @Test
    void mergeToolCallCombinesArgumentsForSameToolCallId() {
        var state = new RoundState();
        var first = new AssistantMessage.ToolCall("id-1", "function", "search", "{\"q\":");
        var second = new AssistantMessage.ToolCall("id-1", "function", "search", "\"test\"}");

        ReactChunkProcessor.mergeToolCall(state, first);
        ReactChunkProcessor.mergeToolCall(state, second);

        assertEquals(1, state.toolCalls.size());
        assertEquals("{\"q\":\"test\"}", state.toolCalls.getFirst().arguments());
    }

    @Test
    void mergeToolCallAppendsDistinctToolCalls() {
        var state = new RoundState();
        ReactChunkProcessor.mergeToolCall(state,
                new AssistantMessage.ToolCall("id-1", "function", "search", "{}"));
        ReactChunkProcessor.mergeToolCall(state,
                new AssistantMessage.ToolCall("id-2", "function", "grep", "{}"));

        assertEquals(2, state.toolCalls.size());
    }
}
