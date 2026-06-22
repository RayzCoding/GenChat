package com.genchat.application.tool.grep;

import java.util.ArrayList;
import java.util.List;

public final class GrepResultFormatter {

    private GrepResultFormatter() {
    }

    static List<String> applyPagination(List<String> lines, int offset, int headLimit) {
        List<String> result = lines;
        if (offset > 0 && offset < lines.size()) {
            result = lines.subList(offset, lines.size());
        }
        if (headLimit > 0 && headLimit < result.size()) {
            result = result.subList(0, headLimit);
        }
        return result;
    }

    public static String format(List<String> lines, String outputMode) {
        if (lines.isEmpty()) {
            return "No matches found.";
        }
        if ("files_with_matches".equals(outputMode) || "count".equals(outputMode)) {
            return String.join("\n", lines);
        }
        return String.join("\n", lines);
    }

    static boolean matchesGlob(String fileName, String glob) {
        if (glob == null || glob.isEmpty() || glob.equals("*")) {
            return true;
        }
        String regex = glob
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return fileName.matches(regex);
    }

    static final class MatchInfo {
        final int lineNumber;
        final String content;
        final String filePath;

        MatchInfo(int lineNumber, String content, String filePath) {
            this.lineNumber = lineNumber;
            this.content = content;
            this.filePath = filePath;
        }
    }

    static List<String> contentMatches(List<String> allLines, java.util.regex.Pattern pattern,
                                       String filePath, int beforeContext, int afterContext) {
        List<String> results = new ArrayList<>();
        List<MatchInfo> matches = new ArrayList<>();
        for (int i = 0; i < allLines.size(); i++) {
            if (pattern.matcher(allLines.get(i)).find()) {
                matches.add(new MatchInfo(i + 1, allLines.get(i), filePath));
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
        return results;
    }
}
