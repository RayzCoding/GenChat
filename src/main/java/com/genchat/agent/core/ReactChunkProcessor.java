package com.genchat.agent.core;

import com.genchat.common.AgentStreamEvent;
import com.genchat.agent.model.RoundMode;
import com.genchat.agent.model.RoundState;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared streaming chunk parsing for ReAct agents.
 */
public final class ReactChunkProcessor {

    private ReactChunkProcessor() {
    }

    public static void processChunk(ChatResponse chunk,
                                    Sinks.Many<String> sink,
                                    RoundState roundState,
                                    boolean jsonTextEvents) {
        if (Objects.isNull(chunk) || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return;
        }
        var text = chunk.getResult().getOutput().getText();
        var toolCalls = chunk.getResult().getOutput().getToolCalls();
        if (!ObjectUtils.isEmpty(toolCalls)) {
            roundState.mode = RoundMode.TOOL_CALL;
            toolCalls.forEach(toolCall -> mergeToolCall(roundState, toolCall));
            return;
        }
        if (StringUtils.hasLength(text)) {
            emitText(sink, text, jsonTextEvents);
            roundState.textBuffer.append(text);
        }
    }

    public static void processForceFinalChunk(ChatResponse chunk,
                                              Sinks.Many<String> sink,
                                              AtomicBoolean hasSentFinalResult,
                                              StringBuilder finalTextBuffer,
                                              boolean jsonTextEvents) {
        if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return;
        }
        var text = chunk.getResult().getOutput().getText();
        if (StringUtils.hasLength(text) && !hasSentFinalResult.get()) {
            emitText(sink, text, jsonTextEvents);
            finalTextBuffer.append(text);
        }
    }

    public static void mergeToolCall(RoundState state, AssistantMessage.ToolCall incoming) {
        for (int i = 0; i < state.toolCalls.size(); i++) {
            AssistantMessage.ToolCall existing = state.toolCalls.get(i);

            if (existing.id().equals(incoming.id())) {
                String mergedArgs = Objects.toString(existing.arguments(), "")
                        + Objects.toString(incoming.arguments(), "");
                state.toolCalls.set(i,
                        new AssistantMessage.ToolCall(existing.id(), "function", existing.name(), mergedArgs)
                );
                return;
            }
        }
        state.toolCalls.add(incoming);
    }

    private static void emitText(Sinks.Many<String> sink, String text, boolean jsonTextEvents) {
        if (jsonTextEvents) {
            sink.tryEmitNext(new AgentStreamEvent.Text(text).toJSON());
        } else {
            sink.tryEmitNext(text);
        }
    }
}
