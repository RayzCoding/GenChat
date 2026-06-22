package com.genchat.application.tool.grep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

public final class JavaGrepExecutor {

    private static final Logger log = LoggerFactory.getLogger(JavaGrepExecutor.class);

    private JavaGrepExecutor() {
    }

    public static List<String> search(GrepSearchRequest request, Charset charset) throws IOException {
        Path searchPath = Paths.get(request.path());
        if (!Files.exists(searchPath)) {
            throw new IOException("Path does not exist: " + request.path());
        }

        int flags = request.ignoreCase() ? Pattern.CASE_INSENSITIVE : 0;
        Pattern regex = Pattern.compile(request.pattern(), flags);

        List<String> resultLines = new ArrayList<>();

        if (Files.isRegularFile(searchPath)) {
            if (GrepResultFormatter.matchesGlob(searchPath.getFileName().toString(), request.glob())) {
                resultLines.addAll(searchFile(searchPath, regex, request, charset));
            }
        } else if (Files.isDirectory(searchPath)) {
            try (Stream<Path> paths = Files.walk(searchPath)) {
                List<Path> files = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> GrepResultFormatter.matchesGlob(p.getFileName().toString(), request.glob()))
                        .collect(Collectors.toList());

                for (Path file : files) {
                    resultLines.addAll(searchFile(file, regex, request, charset));
                }
            }
        }

        return GrepResultFormatter.applyPagination(resultLines, request.offset(), request.headLimit());
    }

    private static List<String> searchFile(Path file, Pattern pattern, GrepSearchRequest request, Charset charset) {
        List<String> results = new ArrayList<>();

        try {
            List<String> allLines = readLinesWithFallback(file, charset);
            if (allLines == null) {
                return results;
            }

            if ("files_with_matches".equals(request.outputMode())) {
                for (String line : allLines) {
                    if (pattern.matcher(line).find()) {
                        results.add(file.toString());
                        return results;
                    }
                }
                return results;
            }

            if ("count".equals(request.outputMode())) {
                long count = allLines.stream()
                        .filter(line -> pattern.matcher(line).find())
                        .count();
                if (count > 0) {
                    results.add(file.toString() + ":" + count);
                }
                return results;
            }

            results.addAll(GrepResultFormatter.contentMatches(
                    allLines, pattern, file.toString(), request.beforeContext(), request.afterContext()));
        } catch (IOException e) {
            log.debug("Skipping file due to IO error: {}", file);
        }
        return results;
    }

    private static List<String> readLinesWithFallback(Path file, Charset charset) throws IOException {
        try {
            return Files.readAllLines(file, charset);
        } catch (MalformedInputException e) {
            log.debug("File is not {} encoded, trying GBK: {}", charset.name(), file);
        }

        try {
            return Files.readAllLines(file, Charset.forName("GBK"));
        } catch (Exception e) {
            log.debug("File is not GBK encoded, trying ISO-8859-1: {}", file);
        }

        try {
            return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            log.warn("Failed to read file with any encoding: {}", file);
            return null;
        }
    }
}
