package com.genchat.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStreamEventTest {

    @Test
    void thinkingEventProducesValidJson() {
        var json = new AgentStreamEvent.Thinking("hello").toJSON();
        assertTrue(json.contains("\"type\":\"thinking\""));
        assertTrue(json.contains("\"content\":\"hello\""));
    }

    @Test
    void textEventProducesValidJson() {
        var json = new AgentStreamEvent.Text("answer").toJSON();
        assertEquals("{\"type\":\"text\",\"content\":\"answer\"}", json);
    }

    @Test
    void toolStartEventIncludesToolName() {
        var json = new AgentStreamEvent.ToolStart("search", "call-1", "{\"query\":\"test\"}").toJSON();
        assertTrue(json.contains("\"type\":\"tool_start\""));
        assertTrue(json.contains("\"toolName\":\"search\""));
    }

    @Test
    void completeEventProducesValidJson() {
        var json = new AgentStreamEvent.Complete().toJSON();
        assertTrue(json.contains("\"type\":\"complete\""));
    }
}
