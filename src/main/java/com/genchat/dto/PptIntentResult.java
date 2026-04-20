package com.genchat.dto;

import lombok.Getter;

/**
 * intent recognize result
 */
@Getter
public class PptIntentResult {
    private final PptIntent intent;
    private final String reason;

    public PptIntentResult(PptIntent intent, String reason) {
        this.intent = intent;
        this.reason = reason;
    }

}
