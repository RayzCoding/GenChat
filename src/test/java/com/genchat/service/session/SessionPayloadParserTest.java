package com.genchat.service.session;

import com.genchat.common.AgentStreamEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionPayloadParserTest {

    @Test
    void parseReferenceFromStreamEventEnvelope() {
        var envelope = AgentStreamEvent.Reference.of(
                "[{\"url\":\"https://example.com\",\"title\":\"Example\",\"content\":\"Body\"}]").toJSON();

        var results = SessionPayloadParser.parseReference(envelope);

        assertEquals(1, results.size());
        assertEquals("https://example.com", results.getFirst().getUrl());
        assertEquals("Example", results.getFirst().getTitle());
        assertEquals("Body", results.getFirst().getContent());
    }

    @Test
    void parseReferenceFromDirectArray() {
        var json = """
                [{"url":"https://a.com","title":"A","content":"alpha"}]
                """;

        var results = SessionPayloadParser.parseReference(json);

        assertEquals(1, results.size());
        assertEquals("https://a.com", results.getFirst().getUrl());
    }

    @Test
    void parseReferenceFromStringifiedContentField() {
        var json = """
                {"type":"reference","content":"[{\\"url\\":\\"https://c.com\\",\\"title\\":\\"C\\",\\"content\\":\\"gamma\\"}]"}
                """;

        var results = SessionPayloadParser.parseReference(json);

        assertEquals(1, results.size());
        assertEquals("https://c.com", results.getFirst().getUrl());
    }

    @Test
    void parseReferenceFromDataField() {
        var json = """
                {"data":[{"url":"https://d.com","title":"D","content":"delta"}]}
                """;

        var results = SessionPayloadParser.parseReference(json);

        assertEquals(1, results.size());
        assertEquals("https://d.com", results.getFirst().getUrl());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "{not-json", "{}", "{\"content\":\"not-an-array\"}"})
    void parseReferenceReturnsEmptyForBlankOrUnrecognized(String input) {
        assertTrue(SessionPayloadParser.parseReference(input).isEmpty());
    }

    @Test
    void parseRecommendFromStreamEventEnvelope() {
        var envelope = AgentStreamEvent.Recommend.of("[\"Question 1\",\"Question 2\"]").toJSON();

        var results = SessionPayloadParser.parseRecommend(envelope);

        assertEquals(List.of("Question 1", "Question 2"), results);
    }

    @Test
    void parseRecommendFromDirectArray() {
        var json = "[\"Follow up A\",\"Follow up B\"]";

        assertEquals(List.of("Follow up A", "Follow up B"), SessionPayloadParser.parseRecommend(json));
    }

    @Test
    void parseRecommendFromStringifiedContentField() {
        var json = """
                {"type":"recommend","content":"[\\"One\\",\\"Two\\"]"}
                """;

        assertEquals(List.of("One", "Two"), SessionPayloadParser.parseRecommend(json));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "{broken", "{\"content\":42}"})
    void parseRecommendReturnsEmptyForBlankOrUnrecognized(String input) {
        assertTrue(SessionPayloadParser.parseRecommend(input).isEmpty());
    }
}
