package com.genchat.agent.deepresearch;

import com.genchat.common.AgentStreamEvent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepResearchStreamsTest {

    @Test
    void parseAndAppendToBuffersHandlesEscapedContent() {
        var finalAnswer = new StringBuilder();
        var thinking = new StringBuilder();
        var chunk = new AgentStreamEvent.Text("line1\nline2").toJSON();

        DeepResearchStreams.parseAndAppendToBuffers(chunk, finalAnswer, thinking);

        assertEquals("line1\nline2", finalAnswer.toString());
    }

    @Test
    void emitThinkingSkipsWhenFinished() {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        var finished = new AtomicBoolean(true);
        var collected = new java.util.ArrayList<String>();
        sink.asFlux().subscribe(collected::add);

        DeepResearchStreams.emitThinking(sink, finished, "should not emit");

        assertTrue(collected.isEmpty());
    }

    @Test
    void renderToolDescriptionsListsAvailableTools() {
        ToolCallback search = mock(ToolCallback.class);
        when(search.getToolDefinition()).thenReturn(
                ToolDefinition.builder().name("search").description("Web search").inputSchema("{}").build());

        var rendered = DeepResearchStreams.renderToolDescriptions(List.of(search));

        assertTrue(rendered.contains("search"));
        assertTrue(rendered.contains("Web search"));
    }

    @Test
    void renderToolDescriptionsShowsPlaceholderWhenEmpty() {
        assertEquals("（Currently, no tools are available）", DeepResearchStreams.renderToolDescriptions(List.of()));
    }

    @Test
    void recordFirstResponseSetsElapsedTimeOnce() {
        var ctx = new DeepResearchRunContext(null);
        DeepResearchStreams.initTimers(ctx);
        ctx.setStartTime(System.currentTimeMillis() - 50);

        DeepResearchStreams.recordFirstResponse(ctx);
        var first = ctx.getFirstResponseTime();

        DeepResearchStreams.recordFirstResponse(ctx);

        assertTrue(first > 0);
        assertEquals(first, ctx.getFirstResponseTime());
    }
}
