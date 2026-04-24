package com.genchat.entity;

import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

import static java.util.Collections.synchronizedList;

/**
 * Agent round execution state
 * Holds intermediate state during each round of execution
 */
@Data
public class RoundState {
    /** Current execution mode */
    public RoundMode mode = RoundMode.UNKNOWN;

    /** Text buffer */
    public StringBuilder textBuffer = new StringBuilder();

    /** Tool call list */
    public List<AssistantMessage.ToolCall> toolCalls = synchronizedList(new java.util.ArrayList<>());

}