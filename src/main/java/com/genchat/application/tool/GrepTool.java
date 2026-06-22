package com.genchat.application.tool;

import com.genchat.application.tool.grep.GrepResultFormatter;
import com.genchat.application.tool.grep.GrepSearchRequest;
import com.genchat.application.tool.grep.JavaGrepExecutor;
import com.genchat.application.tool.grep.RipgrepGrepExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Grep tool - regular-expression file content search.
 */
public class GrepTool {

    private static final Logger log = LoggerFactory.getLogger(GrepTool.class);

    private static final int DEFAULT_HEAD_LIMIT = 250;
    private static final int DEFAULT_CONTEXT_LINES = 0;

    private final boolean useRipgrep;
    private final Charset charset;

    public GrepTool() {
        this(StandardCharsets.UTF_8);
    }

    public GrepTool(Charset charset) {
        this.charset = charset;
        this.useRipgrep = RipgrepGrepExecutor.isAvailable();
        if (useRipgrep) {
            log.info("GrepTool will use ripgrep (rg) for searches");
        } else {
            log.info("GrepTool will use Java native implementation");
        }
    }

    public static ToolCallback[] create() {
        return ToolCallbacks.from(new GrepTool());
    }

    // @formatter:off
    @Tool(name = "grep", description = """
            Regular-expression based file content search tool.

            Usage:
            - Always prefer this tool for content search; do not run grep via bash
            - Full regex syntax supported (e.g. "log.*Error", "function\\\\s+\\\\w+")
            - Filter by file type with glob (e.g. '*.java', '*.tsx')
            - Output modes: 'content' shows matching lines, 'files_with_matches' shows paths only, 'count' shows match counts
            - beforeContext/afterContext control surrounding context lines
            - ignoreCase=true for case-insensitive search
            - headLimit/offset support pagination

            Examples:
            - Search TODO in Java files: pattern='TODO', glob='*.java'
            - Find function definitions: pattern='function\\\\s+\\\\w+', glob='*.js'
            - Case-insensitive search: pattern='error', ignoreCase=true""")
    public String grepContent(
            @ToolParam(description = "[Required] Regular expression pattern to search for") String pattern,
            @ToolParam(description = "File or directory path to search (defaults to current directory)", required = false) String path,
            @ToolParam(description = "File type filter (e.g. '*.java', '*.tsx')", required = false) String glob,
            @ToolParam(description = "Output mode: 'content' (matching lines), 'files_with_matches' (file paths), 'count' (match counts)", required = false) String outputMode,
            @ToolParam(description = "Number of context lines before each match", required = false) Integer beforeContext,
            @ToolParam(description = "Number of context lines after each match", required = false) Integer afterContext,
            @ToolParam(description = "Whether to ignore case (default false)", required = false) Boolean ignoreCase,
            @ToolParam(description = "Maximum number of results to return", required = false) Integer headLimit,
            @ToolParam(description = "Skip first N results", required = false) Integer offset) { // @formatter:on

        log.debug("Grep called: pattern={}, path={}, glob={}, mode={}", pattern, path, glob, outputMode);

        if (outputMode == null || outputMode.isEmpty()) {
            outputMode = "content";
        }
        if (path == null || path.isEmpty()) {
            path = ".";
        }

        var request = new GrepSearchRequest(
                pattern,
                path,
                glob,
                outputMode,
                beforeContext != null ? beforeContext : DEFAULT_CONTEXT_LINES,
                afterContext != null ? afterContext : DEFAULT_CONTEXT_LINES,
                Boolean.TRUE.equals(ignoreCase),
                headLimit != null ? headLimit : DEFAULT_HEAD_LIMIT,
                offset != null ? offset : 0);

        try {
            var lines = useRipgrep
                    ? RipgrepGrepExecutor.search(request, charset)
                    : JavaGrepExecutor.search(request, charset);
            return GrepResultFormatter.format(lines, outputMode);
        } catch (Exception e) {
            log.error("Grep search failed", e);
            return "Error: " + e.getMessage();
        }
    }
}
