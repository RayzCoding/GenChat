package com.genchat.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Token estimation utility.
 * Uses different heuristics for CJK vs Latin text:
 * - English/ASCII: ~4 characters per token
 * - Chinese/CJK: ~1.5 characters per token
 * Lightweight estimate with no external dependencies.
 */
@Slf4j
public final class TokenEstimator {

    private static final double CHARS_PER_TOKEN_EN = 4.0;
    private static final double CHARS_PER_TOKEN_CJK = 1.5;

    private TokenEstimator() {
    }

    public static int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        var counts = new MessageTextExtractor.CharCounts();
        for (Message msg : messages) {
            MessageTextExtractor.countChars(msg, counts);
        }

        int tokens = (int) (counts.cjk / CHARS_PER_TOKEN_CJK + counts.nonCjk / CHARS_PER_TOKEN_EN);

        if (log.isDebugEnabled()) {
            log.debug("Token estimation: cjkChars={}, nonCjkChars={}, estimatedTokens={}, messages={}",
                    counts.cjk, counts.nonCjk, tokens, messages.size());
        }

        return tokens;
    }
}
