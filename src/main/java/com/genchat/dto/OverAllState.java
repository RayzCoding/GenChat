package com.genchat.dto;

import lombok.Data;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

@Data
public class OverAllState {

    private final String conversationId;
    private final String question;
    private final List<Message> messages = new ArrayList<>();
    private int round = 0;
    private String refinedResearchTopic;

    public OverAllState(String conversationId, String question) {
        this.question = question;
        this.conversationId = conversationId;
    }

    public void add(Message m) {
        messages.add(m);
    }

    public String renderFullContext() {
        // Find the index of the most recent Critique Feedback
        int lastCritiqueIndex = findLastCritiqueIndex();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            String text = m.getText();

            // Skip Critique Feedback from earlier rounds
            if (i < lastCritiqueIndex && text != null && text.contains("【Critique Feedback】")) {
                continue;
            }

            sb.append("\n\n[").append(m.getMessageType()).append("]\n\n")
                    .append(text);
        }
        return sb.toString();
    }

    private int findLastCritiqueIndex() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            String text = messages.get(i).getText();
            if (text != null && text.contains("【Critique Feedback】")) {
                return i;
            }
        }
        return -1;
    }

    public int currentChars() {
        return messages.stream()
                .mapToInt(m -> m.getText() == null ? 0 : m.getText().length())
                .sum();
    }

    /**
     * Extract all tool execution results for the summarize phase report.
     */
    public String extractToolResults() {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            String text = m.getText();
            if (text != null && text.contains("【Completed Task Result】")) {
                sb.append(text).append("\n\n");
            }
        }
        return sb.toString();
    }

}
