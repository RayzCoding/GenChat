package com.genchat.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JacksonJsonTest {

    @Test
    void toJsonSerializesObjects() {
        assertEquals("{\"name\":\"genchat\"}", JacksonJson.toJson(java.util.Map.of("name", "genchat")));
    }

    @Test
    void toJsonNullReturnsNullLiteral() {
        assertEquals("null", JacksonJson.toJson(null));
    }

    @Test
    void parseTreeReadsValidJson() {
        var node = JacksonJson.parseTree("{\"type\":\"text\",\"content\":\"hi\"}");
        assertEquals("text", node.path("type").asText());
        assertEquals("hi", node.path("content").asText());
    }

    @Test
    void parseTreeThrowsOnInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> JacksonJson.parseTree("{bad"));
    }

    @Test
    void parseTreeLenientReturnsNullOnInvalidJson() {
        assertNull(JacksonJson.parseTreeLenient("{bad"));
    }

    @Test
    void arraySizeCountsJsonArrayElements() {
        assertEquals(2, JacksonJson.arraySize("[1,2]"));
        assertNull(JacksonJson.arraySize("not-array"));
    }

    @Test
    void textFieldExtractsTopLevelField() {
        assertEquals("value", JacksonJson.textField("{\"field\":\"value\"}", "field"));
        assertNull(JacksonJson.textField("{bad", "field"));
    }

    @Test
    void serializeContentKeepsJsonStructureForJsonStrings() {
        assertEquals("[1,2]", JacksonJson.serializeContent("[1,2]"));
    }

    @Test
    void serializeContentQuotesPlainText() {
        assertEquals("\"plain\"", JacksonJson.serializeContent("plain"));
    }

    @Test
    void fromJsonParsesTypedObjects() {
        record Sample(String name) {
        }
        var sample = JacksonJson.fromJson("{\"name\":\"genchat\"}", Sample.class);
        assertEquals("genchat", sample.name());
    }

    @Test
    void fromJsonLenientReturnsNullOnInvalidJson() {
        assertNull(JacksonJson.fromJsonLenient("{bad", String.class));
    }

    @Test
    void getSafeReadsFieldFromJsonNode() {
        var node = JacksonJson.parseTree("{\"field\":\"value\"}");
        assertEquals("value", JacksonJson.getSafe(node, "field"));
        assertNull(JacksonJson.getSafe(node, "missing"));
    }

    @Test
    void quoteJsonStringEscapesControlCharacters() {
        assertEquals("\"a\\nb\"", JacksonJson.quoteJsonString("a\nb"));
    }
}
