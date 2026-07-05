package com.genchat.context;

import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.common.utils.JacksonJson;
import com.genchat.common.utils.ThinkTagParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context compactor.
 * Runs before each LLM call in the agent loop and compacts the in-memory message list as needed.
 * <p>
 * Layer 1 (micro_compact): runs every round, replacing old tool results and long arguments with placeholders
 * Layer 2 (auto_compact): triggered when tokens exceed the threshold, replacing all old messages with an LLM summary
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextCompactor {
    private final ContextPolicy policy;
    private final ChatModel chatModel;

    /**
     * Compact the message list (backward compatible, without currentQuestion).
     * Mutates the provided messages list in place.
     */
    public void compact(List<Message> messages) {
        compact(messages, null);
    }

    /**
     * Core method: compact the message list. Mutates the provided messages list in place.
     *
     * @param messages        message list
     * @param currentQuestion current user request used to guide summary focus (may be null)
     */
    public void compact(List<Message> messages, String currentQuestion) {
        if (messages == null || messages.size() <= 2) {
            return;
        }

        microCompact(messages);

        int estimatedTokens = TokenEstimator.estimateTokens(messages);
        if (estimatedTokens > policy.tokenThreshold()) {
            log.info("Context auto_compact triggered: estimated tokens={} > threshold={}, messages={}",
                    estimatedTokens, policy.tokenThreshold(), messages.size());
            autoCompact(messages, currentQuestion);
        }
    }

    private void microCompact(List<Message> messages) {
        Map<String, String> toolNameMap = new HashMap<>();
        List<Integer> trmIndices = new ArrayList<>();
        List<Integer> assistantWithToolCallIndices = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg instanceof ToolResponseMessage) {
                trmIndices.add(i);
            } else if (msg instanceof AssistantMessage am
                    && am.getToolCalls() != null && !am.getToolCalls().isEmpty()) {
                assistantWithToolCallIndices.add(i);
                for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                    toolNameMap.put(tc.id(), tc.name());
                }
            }
        }

        compactOldToolResponses(messages, toolNameMap, trmIndices);

        if (policy.maxToolLength() > 0) {
            compactOldToolCallArguments(messages, assistantWithToolCallIndices);
        }
    }

    private void compactOldToolResponses(List<Message> messages,
                                         Map<String, String> toolNameMap,
                                         List<Integer> trmIndices) {
        int trmKeepCount = Math.min(policy.keepRecentTools(), trmIndices.size());
        int trmClearCount = trmIndices.size() - trmKeepCount;
        for (int idx = 0; idx < trmClearCount; idx++) {
            int msgIndex = trmIndices.get(idx);
            ToolResponseMessage original = (ToolResponseMessage) messages.get(msgIndex);
            var compacted = compactToolResponses(original, toolNameMap);
            if (compacted != null) {
                messages.set(msgIndex, compacted);
            }
        }
    }

    private void compactOldToolCallArguments(List<Message> messages, List<Integer> assistantWithToolCallIndices) {
        int ascKeepCount = Math.min(policy.keepRecentTools(), assistantWithToolCallIndices.size());
        int ascClearCount = assistantWithToolCallIndices.size() - ascKeepCount;
        for (int idx = 0; idx < ascClearCount; idx++) {
            int msgIndex = assistantWithToolCallIndices.get(idx);
            AssistantMessage original = (AssistantMessage) messages.get(msgIndex);
            var compacted = compactToolCalls(original);
            if (compacted != null) {
                messages.set(msgIndex, compacted);
            }
        }
    }

    private ToolResponseMessage compactToolResponses(ToolResponseMessage original,
                                                     Map<String, String> toolNameMap) {
        List<ToolResponseMessage.ToolResponse> replaced = new ArrayList<>();
        boolean changed = false;

        for (var resp : original.getResponses()) {
            String content = resp.responseData();
            String toolName = resp.name() != null ? resp.name()
                    : toolNameMap.getOrDefault(resp.id(), "unknown");

            if (policy.isProtected(toolName)) {
                replaced.add(resp);
                continue;
            }

            if (content != null && content.length() > policy.maxToolLength()) {
                content = compactToolPayload(toolName, content.length(), "content compressed");
                changed = true;
            }
            replaced.add(new ToolResponseMessage.ToolResponse(resp.id(), resp.name(), content));
        }

        return changed
                ? ToolResponseMessage.builder().responses(replaced).build()
                : null;
    }

    private AssistantMessage compactToolCalls(AssistantMessage original) {
        List<AssistantMessage.ToolCall> replacedCalls = new ArrayList<>();
        boolean changed = false;

        for (AssistantMessage.ToolCall tc : original.getToolCalls()) {
            if (policy.isProtected(tc.name())) {
                replacedCalls.add(tc);
                continue;
            }

            String args = tc.arguments();
            if (args != null && args.length() > policy.maxToolLength()) {
                args = compactToolPayload(tc.name(), args.length(), "args compressed");
                changed = true;
            }
            replacedCalls.add(new AssistantMessage.ToolCall(tc.id(), tc.type(), tc.name(), args));
        }

        return changed
                ? AssistantMessage.builder()
                .content(original.getText())
                .toolCalls(replacedCalls)
                .build()
                : null;
    }

    private String compactToolPayload(String toolName, int originalLength, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("compacted", true);
        payload.put("tool", toolName != null ? toolName : "unknown");
        payload.put("originalLength", originalLength);
        payload.put("message", message);
        return JacksonJson.toJson(payload);
    }

    private void autoCompact(List<Message> messages, String currentQuestion) {
        List<SystemMessage> systemMessages = new ArrayList<>();
        int systemMsgCount = 0;
        while (systemMsgCount < messages.size()
                && messages.get(systemMsgCount) instanceof SystemMessage sm) {
            systemMessages.add(sm);
            systemMsgCount++;
        }

        List<Message> oldMessages = new ArrayList<>(messages.subList(systemMsgCount, messages.size()));
        String summary = generateSummary(oldMessages, currentQuestion);

        messages.clear();
        messages.addAll(systemMessages);
        messages.add(new UserMessage("[Context compacted] Summary of the prior conversation:\n" + summary));

        log.info("Context compacted: {} old messages summarized into structured summary",
                oldMessages.size());
    }

    private String generateSummary(List<Message> messages, String currentQuestion) {
        String conversationText = buildConversationText(messages);

        try {
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(ReactAgentPrompts.getCompactSummarySystemPrompt()),
                    new UserMessage(ReactAgentPrompts.getCompactSummaryUserPrompt(conversationText, currentQuestion))
            )));
            String summary = response.getResult().getOutput().getText();
            summary = ThinkTagParser.stripThinkTags(summary);

            log.info("Context summary generated: {} chars input -> {} chars summary",
                    conversationText.length(), summary != null ? summary.length() : 0);

            return summary != null ? summary : "";
        } catch (Exception e) {
            log.warn("LLM summary failed, falling back to message-level truncation: {}", e.getMessage());
            return truncationFallback(messages);
        }
    }

    private String truncationFallback(List<Message> messages) {
        int keepCount = policy.truncationKeepMessages();
        int start = Math.max(0, messages.size() - keepCount);
        StringBuilder sb = new StringBuilder();
        sb.append("...[Summary generation failed; showing the most recent ")
                .append(keepCount)
                .append(" messages]\n\n");
        for (int i = start; i < messages.size(); i++) {
            sb.append(MessageTextExtractor.extractText(messages.get(i))).append("\n\n");
        }
        return sb.toString();
    }

    private String buildConversationText(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            sb.append("[").append(msg.getMessageType()).append("] ");
            sb.append(MessageTextExtractor.extractText(msg));
            sb.append("\n\n");
        }
        return sb.toString();
    }
}
