package com.genchat.application.stream;

import com.genchat.service.AiChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

@Slf4j
public final class PersistentChatMemoryLoader {

    public static final int DEFAULT_MAX_MESSAGES = 30;

    private PersistentChatMemoryLoader() {
    }

    public static ChatMemory load(AiChatSessionService sessionService, String conversationId) {
        return load(sessionService, conversationId, DEFAULT_MAX_MESSAGES);
    }

    public static ChatMemory load(AiChatSessionService sessionService,
                                  String conversationId,
                                  int maxMessages) {
        var historyMessages = sessionService.queryRecentBySessionId(conversationId, maxMessages);
        var memory = MessageWindowChatMemory.builder().maxMessages(maxMessages).build();
        if (!CollectionUtils.isEmpty(historyMessages)) {
            historyMessages.forEach(message -> {
                var userQuestion = message.getQuestion();
                var systemAnswer = message.getAnswer();
                if (!ObjectUtils.isEmpty(userQuestion)) {
                    memory.add(conversationId, new UserMessage(userQuestion));
                }
                if (!ObjectUtils.isEmpty(systemAnswer)) {
                    memory.add(conversationId, new AssistantMessage(systemAnswer));
                }
            });
            log.info("Loading history messages, conversationId: {}, recordCount: {}",
                    conversationId, historyMessages.size());
        }
        return memory;
    }
}
