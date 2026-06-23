package com.genchat.application.stream;

import com.genchat.common.AgentStreamEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamChunkAccumulatorTest {

    @Test
    void accumulatesTextAndThinkingChunks() {
        var finalAnswer = new StringBuilder();
        var thinking = new StringBuilder();

        StreamChunkAccumulator.append(new AgentStreamEvent.Text("hello ").toJSON(), finalAnswer, thinking);
        StreamChunkAccumulator.append(new AgentStreamEvent.Thinking("think ").toJSON(), finalAnswer, thinking);
        StreamChunkAccumulator.append(new AgentStreamEvent.Text("world").toJSON(), finalAnswer, thinking);

        assertEquals("hello world", finalAnswer.toString());
        assertEquals("think ", thinking.toString());
    }

    @Test
    void treatsInvalidJsonAsRawText() {
        var finalAnswer = new StringBuilder();
        var thinking = new StringBuilder();

        StreamChunkAccumulator.append("not-json", finalAnswer, thinking);

        assertEquals("not-json", finalAnswer.toString());
        assertEquals("", thinking.toString());
    }

    @Test
    void ignoresNonTextThinkingEventTypes() {
        var finalAnswer = new StringBuilder();
        var thinking = new StringBuilder();
        var toolStart = new AgentStreamEvent.ToolStart("search", "id-1", "{}").toJSON();

        StreamChunkAccumulator.append(toolStart, finalAnswer, thinking);

        assertEquals("", finalAnswer.toString());
        assertEquals("", thinking.toString());
    }
}
