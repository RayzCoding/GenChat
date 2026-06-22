package com.genchat.application.tool.grep;

public record GrepSearchRequest(
        String pattern,
        String path,
        String glob,
        String outputMode,
        int beforeContext,
        int afterContext,
        boolean ignoreCase,
        int headLimit,
        int offset
) {
}
