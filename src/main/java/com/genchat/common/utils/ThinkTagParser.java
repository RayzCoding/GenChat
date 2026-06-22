package com.genchat.common.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * &lt;think/&gt; tag parser.
 *
 * Stateless utility that splits LLM streaming text chunks into thinking content and normal text.
 * Supports cross-chunk tag state tracking via the inThink parameter.
 *
 * @author bigchui
 */
public final class ThinkTagParser {

    private static final String THINK_START = "<think";
    private static final String THINK_END = "</think";

    private ThinkTagParser() {
    }

    /**
     * Strip think tags and their content from text.
     *
     * @param input text that may contain think tags
     * @return text with think tags removed
     */
    public static String stripThinkTags(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // Generic regex: match <think...>...</think...> (spaces, attributes, self-closing variants)
        String result = input.replaceAll("(?s)<think[^>]*>.*?</think[^>]*>", "").trim();
        return result;
    }

    /**
     * Content segment indicating thinking content or normal text.
     */
    public record Segment(boolean thinking, String content) {
    }

    /**
     * Parse result.
     */
    public record ParseResult(List<Segment> segments, boolean inThink) {
    }

    /**
     * Parse one text chunk.
     *
     * @param chunk   current text chunk
     * @param inThink whether the previous chunk ended inside a think tag
     * @return parse result with split segments and updated inThink state
     */
    public static ParseResult parse(String chunk, boolean inThink) {
        if (chunk == null || chunk.isEmpty()) {
            return new ParseResult(List.of(), inThink);
        }

        List<Segment> segments = new ArrayList<>();
        boolean currentInThink = inThink;
        int index = 0;

        while (index < chunk.length()) {
            int thinkStartIdx = chunk.indexOf(THINK_START, index);
            int thinkEndIdx = chunk.indexOf(THINK_END, index);

            int nextTagPos;
            boolean isStartTag;

            if (thinkStartIdx == -1 && thinkEndIdx == -1) {
                String remaining = chunk.substring(index);
                if (!remaining.isEmpty()) {
                    segments.add(new Segment(currentInThink, remaining));
                }
                break;
            }

            if (thinkStartIdx != -1 && (thinkEndIdx == -1 || thinkStartIdx < thinkEndIdx)) {
                nextTagPos = thinkStartIdx;
                isStartTag = true;
            } else {
                nextTagPos = thinkEndIdx;
                isStartTag = false;
            }

            if (nextTagPos > index) {
                String beforeTag = chunk.substring(index, nextTagPos);
                if (!beforeTag.isEmpty()) {
                    segments.add(new Segment(currentInThink, beforeTag));
                }
            }

            int tagEnd = chunk.indexOf('>', nextTagPos);
            if (tagEnd == -1) {
                currentInThink = isStartTag;
                break;
            }

            currentInThink = isStartTag;
            index = tagEnd + 1;
        }

        return new ParseResult(segments, currentInThink);
    }
}
