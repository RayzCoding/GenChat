package com.genchat.context;

import com.genchat.common.utils.ThinkTagParser;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Shared message text extraction and character counting for context compaction.
 */
public final class MessageTextExtractor {

    private MessageTextExtractor() {
    }

    public static String extractText(Message msg) {
        if (msg instanceof AssistantMessage am) {
            StringBuilder sb = new StringBuilder();
            appendAssistantText(sb, am);
            return sb.toString();
        }
        if (msg instanceof ToolResponseMessage trm) {
            StringBuilder sb = new StringBuilder();
            appendToolResponseText(sb, trm);
            return sb.toString();
        }
        if (msg instanceof SystemMessage sm) {
            return sm.getText() != null ? sm.getText() : "";
        }
        if (msg instanceof UserMessage um) {
            return um.getText() != null ? um.getText() : "";
        }
        return msg.toString();
    }

    public static void countChars(Message message, CharCounts counts) {
        if (message instanceof SystemMessage sm) {
            countString(sm.getText(), counts);
        } else if (message instanceof UserMessage um) {
            countString(um.getText(), counts);
        } else if (message instanceof AssistantMessage am) {
            countString(am.getText(), counts);
            if (am.getToolCalls() != null) {
                for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                    countString(tc.name(), counts);
                    countString(tc.arguments(), counts);
                }
            }
        } else if (message instanceof ToolResponseMessage trm) {
            for (ToolResponseMessage.ToolResponse resp : trm.getResponses()) {
                countString(resp.responseData(), counts);
            }
        } else {
            countString(message.toString(), counts);
        }
    }

    public static void countString(String text, CharCounts counts) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                counts.cjk++;
            } else {
                counts.nonCjk++;
            }
        }
    }

    private static void appendAssistantText(StringBuilder sb, AssistantMessage am) {
        String text = am.getText() != null ? am.getText() : "";
        sb.append(ThinkTagParser.stripThinkTags(text));
        if (am.getToolCalls() != null) {
            for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                sb.append("\n[ToolCall: ").append(tc.name()).append(" args=").append(tc.arguments()).append("]");
            }
        }
    }

    private static void appendToolResponseText(StringBuilder sb, ToolResponseMessage trm) {
        for (var resp : trm.getResponses()) {
            sb.append(resp.responseData() != null ? resp.responseData() : "");
        }
    }

    private static boolean isCjk(char ch) {
        return (ch >= '\u4E00' && ch <= '\u9FFF')
                || (ch >= '\u3400' && ch <= '\u4DBF')
                || (ch >= '\uF900' && ch <= '\uFAFF')
                || (ch >= '\u2E80' && ch <= '\u2EFF')
                || (ch >= '\u3000' && ch <= '\u303F')
                || (ch >= '\uFF00' && ch <= '\uFFEF')
                || (ch >= '\u3040' && ch <= '\u309F')
                || (ch >= '\u30A0' && ch <= '\u30FF');
    }

    static final class CharCounts {
        int cjk;
        int nonCjk;
    }
}
