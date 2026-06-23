package com.genchat.agent.deepresearch;

import com.genchat.application.stream.StreamChunkAccumulator;
import com.genchat.common.AgentStreamEvent;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DeepResearchStreams {

    private DeepResearchStreams() {
    }

    public static void emitThinking(Sinks.Many<String> sink, AtomicBoolean finished, String content) {
        if (finished.get()) {
            return;
        }
        sink.tryEmitNext(new AgentStreamEvent.Thinking(content).toJSON());
    }

    public static void parseAndAppendToBuffers(String chunk, StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer) {
        StreamChunkAccumulator.append(chunk, finalAnswerBuffer, thinkingBuffer);
    }

    public static String renderToolDescriptions(List<ToolCallback> tools) {
        if (tools == null || tools.isEmpty()) {
            return "（Currently, no tools are available）";
        }
        StringBuilder sb = new StringBuilder();
        for (ToolCallback tool : tools) {
            sb.append("- ")
                    .append(tool.getToolDefinition().name())
                    .append(": ")
                    .append(tool.getToolDefinition().description())
                    .append("\n");
        }
        return sb.toString();
    }

    public static void recordFirstResponse(DeepResearchRunContext ctx) {
        if (ctx.getFirstResponseTime() == 0 && ctx.getStartTime() > 0) {
            ctx.setFirstResponseTime(System.currentTimeMillis() - ctx.getStartTime());
        }
    }

    public static void initTimers(DeepResearchRunContext ctx) {
        ctx.setStartTime(System.currentTimeMillis());
        ctx.setFirstResponseTime(0);
    }
}
