package com.genchat.agent.stream;

import com.genchat.agent.deepresearch.DeepResearchStreams;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.utils.JacksonJson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization tests for SSE chunk accumulation used across agents.
 */
class StreamBufferParsingTest {

    @Test
    void jacksonParserAccumulatesTextAndThinkingChunks() {
        var finalAnswer = new StringBuilder();
        var thinking = new StringBuilder();

        appendWithJackson(new AgentStreamEvent.Text("hello ").toJSON(), finalAnswer, thinking);
        appendWithJackson(new AgentStreamEvent.Thinking("think ").toJSON(), finalAnswer, thinking);
        appendWithJackson(new AgentStreamEvent.Text("world").toJSON(), finalAnswer, thinking);

        assertEquals("hello world", finalAnswer.toString());
        assertEquals("think ", thinking.toString());
    }

    @Test
    void deepResearchParserAccumulatesTextAndThinkingChunks() {
        var finalAnswer = new StringBuilder();
        var thinking = new StringBuilder();

        DeepResearchStreams.parseAndAppendToBuffers(
                new AgentStreamEvent.Text("hello ").toJSON(), finalAnswer, thinking);
        DeepResearchStreams.parseAndAppendToBuffers(
                new AgentStreamEvent.Thinking("think ").toJSON(), finalAnswer, thinking);
        DeepResearchStreams.parseAndAppendToBuffers(
                new AgentStreamEvent.Text("world").toJSON(), finalAnswer, thinking);

        assertEquals("hello world", finalAnswer.toString());
        assertEquals("think ", thinking.toString());
    }

    @Test
    void bothParsersTreatInvalidJsonAsRawText() {
        var jacksonFinal = new StringBuilder();
        var jacksonThinking = new StringBuilder();
        appendWithJackson("not-json", jacksonFinal, jacksonThinking);

        var deepFinal = new StringBuilder();
        var deepThinking = new StringBuilder();
        DeepResearchStreams.parseAndAppendToBuffers("not-json", deepFinal, deepThinking);

        assertEquals("not-json", jacksonFinal.toString());
        assertEquals("", jacksonThinking.toString());
        assertEquals("not-json", deepFinal.toString());
        assertEquals("", deepThinking.toString());
    }

    @Test
    void bothParsersIgnoreNonTextThinkingEventTypes() {
        var toolStart = new AgentStreamEvent.ToolStart("search", "id-1", "{}").toJSON();

        var jacksonFinal = new StringBuilder();
        var jacksonThinking = new StringBuilder();
        appendWithJackson(toolStart, jacksonFinal, jacksonThinking);

        var deepFinal = new StringBuilder();
        var deepThinking = new StringBuilder();
        DeepResearchStreams.parseAndAppendToBuffers(toolStart, deepFinal, deepThinking);

        assertEquals("", jacksonFinal.toString());
        assertEquals("", jacksonThinking.toString());
        assertEquals("", deepFinal.toString());
        assertEquals("", deepThinking.toString());
    }

    /**
     * Mirrors {@code AbstractReactAgent#streamInternal} doOnNext accumulation logic.
     */
    private static void appendWithJackson(String chunk, StringBuilder finalAnswer, StringBuilder thinking) {
        try {
            var json = JacksonJson.parseTreeLenient(chunk);
            if (json != null) {
                String type = json.path("type").asText(null);
                if ("text".equals(type)) {
                    finalAnswer.append(json.path("content").asText(""));
                } else if ("thinking".equals(type)) {
                    thinking.append(json.path("content").asText(""));
                }
            } else {
                finalAnswer.append(chunk);
            }
        } catch (Exception e) {
            finalAnswer.append(chunk);
        }
    }
}
