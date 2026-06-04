package com.genchat.application.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Grep Tool - powerful regular-expression search tool
 *
 *
 */
public class GrepTool {

    private static final Logger log = LoggerFactory.getLogger(GrepTool.class);

    private static final int DEFAULT_HEAD_LIMIT = 250;
    private static final boolean DEFAULT_CASE_SENSITIVE = false;
    private static final int DEFAULT_CONTEXT_LINES = 0;

    private final boolean useRipgrep;
    private final Charset charset;

    /**
     * Default constructor; auto-detects ripgrep availability
     */
    public GrepTool() {
        this(StandardCharsets.UTF_8);
    }

    /**
     * Constructor with explicit charset
     *
     * @param charset file encoding
     */
    public GrepTool(Charset charset) {
        this.charset = charset;
        this.useRipgrep = checkRipgrepAvailable();
        if (useRipgrep) {
            log.info("GrepTool will use ripgrep (rg) for searches");
        } else {
            log.info("GrepTool will use Java native implementation");
        }
    }

    /**
     * Creates ToolCallback array for the Grep tool (default configuration)
     *
     * Convenience method that instantiates the tool with default settings.
     *
     * @return ToolCallback array containing the grep tool
     */
    public static ToolCallback[] create() {
        return ToolCallbacks.from(new GrepTool());
    }

    /**
     * Checks whether ripgrep is installed on the system
     *
     * @return true if available
     */
    private boolean checkRipgrepAvailable() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"rg", "--version"});
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Main grep search entry point
     *
     * @param pattern regular expression pattern
     * @param path search path (file or directory)
     * @param glob file type filter (e.g. "*.java")
     * @param outputMode output mode: content/files_with_matches/count
     * @param beforeContext lines of context before match (-B)
     * @param afterContext lines of context after match (-A)
     * @param ignoreCase whether to ignore case (-i)
     * @param headLimit maximum number of output lines
     * @param offset skip first N lines
     * @return formatted search results
     */
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

        log.debug("Grep called: pattern={}, path={}, glob={}, mode={}",
            pattern, path, glob, outputMode);

        // Apply defaults for optional params to avoid NPE when LLM omits them
        if (outputMode == null || outputMode.isEmpty()) outputMode = "content";
        if (path == null || path.isEmpty()) path = ".";
        boolean ignoreCaseVal = Boolean.TRUE.equals(ignoreCase);
        int beforeContextVal = beforeContext != null ? beforeContext : DEFAULT_CONTEXT_LINES;
        int afterContextVal = afterContext != null ? afterContext : DEFAULT_CONTEXT_LINES;
        int headLimitVal = headLimit != null ? headLimit : DEFAULT_HEAD_LIMIT;
        int offsetVal = offset != null ? offset : 0;

        try {
            if (useRipgrep) {
                return searchWithRipgrep(pattern, path, glob, outputMode,
                    beforeContextVal, afterContextVal, ignoreCaseVal, headLimitVal, offsetVal);
            } else {
                return searchWithJava(pattern, path, glob, outputMode,
                    beforeContextVal, afterContextVal, ignoreCaseVal, headLimitVal, offsetVal);
            }
        } catch (Exception e) {
            log.error("Grep search failed", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Search using ripgrep
     */
    private String searchWithRipgrep(
            String pattern,
            String path,
            String glob,
            String outputMode,
            int beforeContext,
            int afterContext,
            boolean ignoreCase,
            int headLimit,
            int offset) throws IOException, InterruptedException {

        List<String> command = new ArrayList<>();
        command.add("rg");

        // Add pattern
        command.add(pattern);

        // Add path
        if (path != null && !path.isEmpty()) {
            command.add(path);
        }

        // Add glob filter
        if (glob != null && !glob.isEmpty()) {
            command.add("--glob");
            command.add(glob);
        }

        // Context options
        if (beforeContext > 0 || afterContext > 0) {
            command.add("-C");
            command.add(String.valueOf(Math.max(beforeContext, afterContext)));
        }

        // Case insensitivity
        if (ignoreCase) {
            command.add("-i");
        }

        // Line numbers (on by default)
        command.add("-n");

        // Output mode
        switch (outputMode) {
            case "files_with_matches":
                command.add("-l");
                break;
            case "count":
                command.add("-c");
                break;
            case "content":
            default:
                // content mode needs no extra flags
                break;
        }

        // Execute command
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read output
        List<String> lines = new BufferedReader(new InputStreamReader(
            process.getInputStream(), charset))
            .lines()
            .collect(Collectors.toList());

        process.waitFor();

        // Apply offset and headLimit
        List<String> result = lines;
        if (offset > 0 && offset < lines.size()) {
            result = lines.subList(offset, lines.size());
        }
        if (headLimit > 0 && headLimit < result.size()) {
            result = result.subList(0, headLimit);
        }

        return formatResult(result, outputMode);
    }

    /**
     * Search using Java native implementation
     */
    private String searchWithJava(
            String pattern,
            String path,
            String glob,
            String outputMode,
            int beforeContext,
            int afterContext,
            boolean ignoreCase,
            int headLimit,
            int offset) throws IOException {

        Path searchPath = Paths.get(path);
        if (!Files.exists(searchPath)) {
            return "Error: Path does not exist: " + path;
        }

        // Compile regex
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
        Pattern regex = Pattern.compile(pattern, flags);

        List<String> resultLines = new ArrayList<>();

        // Single file
        if (Files.isRegularFile(searchPath)) {
            if (matchesGlob(searchPath.getFileName().toString(), glob)) {
                List<String> fileResults = searchFile(searchPath, regex,
                    outputMode, beforeContext, afterContext);
                resultLines.addAll(fileResults);
            }
        } else if (Files.isDirectory(searchPath)) {
            // Walk directory
            try (Stream<Path> paths = Files.walk(searchPath)) {
                List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> matchesGlob(p.getFileName().toString(), glob))
                    .collect(Collectors.toList());

                for (Path file : files) {
                    List<String> fileResults = searchFile(file, regex,
                        outputMode, beforeContext, afterContext);
                    resultLines.addAll(fileResults);
                }
            }
        }

        // Apply offset and headLimit
        List<String> result = resultLines;
        if (offset > 0 && offset < result.size()) {
            result = result.subList(offset, result.size());
        }
        if (headLimit > 0 && headLimit < result.size()) {
            result = result.subList(0, headLimit);
        }

        return formatResult(result, outputMode);
    }

    /**
     * Search a single file
     */
    private List<String> searchFile(
            Path file,
            Pattern pattern,
            String outputMode,
            int beforeContext,
            int afterContext) {

        List<String> results = new ArrayList<>();

        try {
            List<String> allLines = readLinesWithFallback(file);
            if (allLines == null) {
                return results;
            }

            if ("files_with_matches".equals(outputMode)) {
                for (String line : allLines) {
                    if (pattern.matcher(line).find()) {
                        results.add(file.toString());
                        return results;
                    }
                }
                return results;
            }

            if ("count".equals(outputMode)) {
                long count = allLines.stream()
                    .filter(line -> pattern.matcher(line).find())
                    .count();
                if (count > 0) {
                    results.add(file.toString() + ":" + count);
                }
                return results;
            }

            // content mode - search and add context
            List<MatchInfo> matches = new ArrayList<>();
            for (int i = 0; i < allLines.size(); i++) {
                if (pattern.matcher(allLines.get(i)).find()) {
                    matches.add(new MatchInfo(i + 1, allLines.get(i), file.toString()));
                }
            }

            for (MatchInfo match : matches) {
                int startLine = Math.max(0, match.lineNumber - beforeContext - 1);
                int endLine = Math.min(allLines.size(), match.lineNumber + afterContext);
                for (int i = startLine; i < endLine; i++) {
                    String prefix = (i == match.lineNumber - 1) ? ":" : "-";
                    results.add(String.format("%s%s:%d:%s",
                        match.filePath, prefix, i + 1, allLines.get(i)));
                }
            }
        } catch (IOException e) {
            log.debug("Skipping file due to IO error: {}", file);
        }
        return results;
    }

    /**
     * Read all lines from a file with encoding fallback.
     * UTF-8 → GBK → ISO-8859-1 so files in almost any encoding can be read.
     */
    private List<String> readLinesWithFallback(Path file) throws IOException {
        // 1. Try UTF-8
        try {
            return Files.readAllLines(file, charset);
        } catch (MalformedInputException e) {
            log.debug("File is not {} encoded, trying GBK: {}", charset.name(), file);
        }

        // 2. Try GBK (common on Windows for Chinese text)
        try {
            return Files.readAllLines(file, Charset.forName("GBK"));
        } catch (Exception e) {
            log.debug("File is not GBK encoded, trying ISO-8859-1: {}", file);
        }

        // 3. ISO-8859-1 (single-byte; never throws MalformedInputException)
        try {
            return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            log.warn("Failed to read file with any encoding: {}", file);
            return null;
        }
    }

    /**
     * Simple glob matching
     */
    private boolean matchesGlob(String fileName, String glob) {
        if (glob == null || glob.isEmpty() || glob.equals("*")) {
            return true;
        }

        // Convert glob to regex
        String regex = glob
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        return fileName.matches(regex);
    }

    /**
     * Format search results
     */
    private String formatResult(List<String> lines, String outputMode) {
        if (lines.isEmpty()) {
            return "No matches found.";
        }

        if ("files_with_matches".equals(outputMode) || "count".equals(outputMode)) {
            return String.join("\n", lines);
        }

        // content mode
        return String.join("\n", lines);
    }

    /**
     * Match metadata for content output
     */
    private static class MatchInfo {
        final int lineNumber;
        final String content;
        final String filePath;

        MatchInfo(int lineNumber, String content, String filePath) {
            this.lineNumber = lineNumber;
            this.content = content;
            this.filePath = filePath;
        }
    }
}
