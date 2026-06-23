package com.genchat.common;

import com.genchat.common.utils.JacksonJson;
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
    void thinkingEventEscapesSpecialCharacters() {
        var json = new AgentStreamEvent.Thinking("say \"hi\"\n").toJSON();
        assertEquals("{\"type\":\"thinking\",\"content\":\"say \\\"hi\\\"\\n\"}", json);
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
        assertTrue(json.contains("\"toolCallId\":\"call-1\""));
    }

    @Test
    void toolEndEventIncludesResult() {
        var json = new AgentStreamEvent.ToolEnd("search", "call-1", "result-body").toJSON();
        assertEquals("{\"type\":\"tool_end\",\"toolName\":\"search\",\"toolCallId\":\"call-1\",\"result\":\"result-body\"}", json);
    }

    @Test
    void completeEventProducesValidJson() {
        var json = new AgentStreamEvent.Complete().toJSON();
        assertEquals("{\"type\":\"complete\"}", json);
    }

    @Test
    void referenceEventCountsJsonArrayItems() {
        var json = AgentStreamEvent.Reference.of("[{\"url\":\"https://example.com\"}]").toJSON();
        assertTrue(json.contains("\"type\":\"reference\""));
        assertTrue(json.contains("\"count\":1"));
    }

    @Test
    void referenceEventEmbedsArrayContentDirectly() {
        var json = AgentStreamEvent.Reference.of("[{\"url\":\"https://example.com\"}]").toJSON();
        assertTrue(json.contains("\"content\":[{\"url\":\"https://example.com\"}]"));
    }

    @Test
    void recommendEventCountsJsonArrayItems() {
        var json = AgentStreamEvent.Recommend.of("[\"Q1\",\"Q2\"]").toJSON();
        assertTrue(json.contains("\"type\":\"recommend\""));
        assertTrue(json.contains("\"count\":2"));
    }

    @Test
    void errorEventIncludesCodeMessageAndContentAlias() {
        var json = new AgentStreamEvent.Error("LLM_CALL_FAILED", "retry", "timeout").toJSON();
        assertTrue(json.contains("\"type\":\"error\""));
        assertTrue(json.contains("\"code\":\"LLM_CALL_FAILED\""));
        assertTrue(json.contains("\"message\":\"retry\""));
        assertTrue(json.contains("\"content\":\"retry\""));
        assertTrue(json.contains("\"detail\":\"timeout\""));
    }

    @Test
    void errorSimpleFactoryUsesDefaultCode() {
        var json = AgentStreamEvent.Error.simple("failed").toJSON();
        assertTrue(json.contains("\"code\":\"ERROR\""));
        assertTrue(json.contains("\"detail\":null"));
    }

    @Test
    void jacksonJsonStopMessageMatchesLegacyShape() {
        var json = JacksonJson.stopMessageJson("stop");
        assertEquals("{\"type\":\"text\",\"content\":\"stop\"}", json);
    }

    @Test
    void eventsRoundTripThroughJacksonParser() {
        var original = new AgentStreamEvent.Thinking("round-trip").toJSON();
        var node = JacksonJson.parseTree(original);

        assertEquals("thinking", node.path("type").asText());
        assertEquals("round-trip", node.path("content").asText());
    }

    @Test
    void nullDetailSerializesAsJsonNullInErrorEvent() {
        var json = new AgentStreamEvent.Error("E", "msg", null).toJSON();
        var node = JacksonJson.parseTree(json);
        assertTrue(node.get("detail").isNull());
    }
}
