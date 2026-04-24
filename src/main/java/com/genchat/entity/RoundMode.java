package com.genchat.entity;

import lombok.Getter;

/**
 * Agent round mode enum
 * UNKNOWN: Unknown mode
 * FINAL_ANSWER: Final answer mode
 * TOOL_CALL: Tool call mode
 */
@Getter
public enum RoundMode {
    /**
     * Unknown mode
     */
    UNKNOWN("unknown"),
    /**
     * Final answer mode
     */
    FINAL_ANSWER("final_answer"),
    /**
     * Tool call mode
     */
    TOOL_CALL("tool_call");

    private final String desc;

    RoundMode(String desc) {
        this.desc = desc;
    }
}
