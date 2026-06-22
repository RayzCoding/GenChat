package com.genchat.common.prompts;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads prompt templates from classpath {@code prompts/} markdown files.
 */
public final class PromptLoader {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private PromptLoader() {
    }

    public static String load(String relativePath) {
        return CACHE.computeIfAbsent(relativePath, PromptLoader::readClasspath);
    }

    public static String format(String relativePath, Object... args) {
        return load(relativePath).formatted(args);
    }

    private static String readClasspath(String relativePath) {
        try {
            var resource = new ClassPathResource("prompts/" + relativePath);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt template: " + relativePath, e);
        }
    }
}
