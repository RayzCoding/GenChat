package com.genchat.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genchat.entity.AgentState;
import com.genchat.entity.SearchResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static com.genchat.common.utils.JsonUtil.getSafe;

/**
 * Shared ReAct tool execution helpers used by streaming and blocking agents.
 */
public final class ReactToolSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReactToolSupport() {
    }

    public static ToolCallback findTool(List<ToolCallback> tools, String name) {
        return tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static void addErrorToolResponse(List<Message> messages,
                                            AssistantMessage.ToolCall toolCall,
                                            String errMsg) {
        ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                toolCall.id(),
                toolCall.name(),
                "{ \"error\": \"" + errMsg + "\" }"
        );
        messages.add(ToolResponseMessage.builder()
                .responses(List.of(tr))
                .build());
    }

    public static void completeToolCall(AtomicInteger completedCount,
                                       int total,
                                       Map<String, ToolResponseMessage.ToolResponse> responseMap,
                                       List<AssistantMessage.ToolCall> toolCalls,
                                       List<Message> messages,
                                       Runnable onComplete) {
        int current = completedCount.incrementAndGet();
        if (current >= total) {
            List<ToolResponseMessage.ToolResponse> sortedResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : toolCalls) {
                ToolResponseMessage.ToolResponse response = responseMap.get(tc.id());
                if (response != null) {
                    sortedResponses.add(response);
                } else {
                    sortedResponses.add(new ToolResponseMessage.ToolResponse(
                            tc.id(), tc.name(), "{ \"error\": \"Tool response is missing\" }"));
                }
            }
            messages.add(ToolResponseMessage.builder().responses(sortedResponses).build());
            onComplete.run();
        }
    }

    public static void parseTavilySearchResult(String resultJson, AgentState state) {
        if (state == null) {
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(resultJson);
            if (!root.isArray() || root.isEmpty()) {
                return;
            }

            JsonNode first = root.get(0);
            JsonNode textNode = first.get("text");
            if (textNode == null || textNode.isNull()) {
                return;
            }

            JsonNode textJson = textNode.isTextual()
                    ? MAPPER.readTree(textNode.asText())
                    : textNode;

            JsonNode results = textJson.get("results");
            if (results == null || !results.isArray()) {
                return;
            }

            for (JsonNode item : results) {
                String url = getSafe(item, "url");
                String title = getSafe(item, "title");
                String content = getSafe(item, "content");
                if (url != null && !url.isBlank()) {
                    state.searchResults.add(new SearchResult(url, title, content));
                }
            }
        } catch (Exception ignored) {
            // Ignore malformed tool payloads during search result parsing.
        }
    }

    public static String toolErrorPayload(String message) {
        return "{ \"error\": \"" + Objects.toString(message, "unknown error") + "\" }";
    }
}
