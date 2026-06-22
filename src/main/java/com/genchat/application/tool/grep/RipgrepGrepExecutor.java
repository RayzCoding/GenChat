package com.genchat.application.tool.grep;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class RipgrepGrepExecutor {

    private RipgrepGrepExecutor() {
    }

    public static boolean isAvailable() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"rg", "--version"});
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public static List<String> search(GrepSearchRequest request, Charset charset)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("rg");
        command.add(request.pattern());

        if (request.path() != null && !request.path().isEmpty()) {
            command.add(request.path());
        }

        if (request.glob() != null && !request.glob().isEmpty()) {
            command.add("--glob");
            command.add(request.glob());
        }

        if (request.beforeContext() > 0 || request.afterContext() > 0) {
            command.add("-C");
            command.add(String.valueOf(Math.max(request.beforeContext(), request.afterContext())));
        }

        if (request.ignoreCase()) {
            command.add("-i");
        }

        command.add("-n");

        switch (request.outputMode()) {
            case "files_with_matches" -> command.add("-l");
            case "count" -> command.add("-c");
            default -> {
            }
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> lines = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))
                .lines()
                .collect(Collectors.toList());

        process.waitFor();
        return GrepResultFormatter.applyPagination(lines, request.offset(), request.headLimit());
    }
}
