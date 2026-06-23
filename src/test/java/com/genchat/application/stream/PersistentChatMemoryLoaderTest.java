package com.genchat.application.stream;

import com.genchat.dto.AiChatSession;
import com.genchat.service.AiChatSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistentChatMemoryLoaderTest {

    @Mock
    private AiChatSessionService sessionService;

    @Test
    void loadReturnsEmptyMemoryWhenNoHistory() {
        when(sessionService.queryRecentBySessionId("conv-1", PersistentChatMemoryLoader.DEFAULT_MAX_MESSAGES))
                .thenReturn(List.of());

        var memory = PersistentChatMemoryLoader.load(sessionService, "conv-1");

        assertTrue(memory.get("conv-1").isEmpty());
    }

    @Test
    void loadRestoresUserAndAssistantMessages() {
        when(sessionService.queryRecentBySessionId("conv-2", PersistentChatMemoryLoader.DEFAULT_MAX_MESSAGES))
                .thenReturn(List.of(
                        AiChatSession.builder()
                                .question("Hello")
                                .answer("Hi there")
                                .build()
                ));

        var memory = PersistentChatMemoryLoader.load(sessionService, "conv-2");
        var messages = memory.get("conv-2");

        assertEquals(2, messages.size());
        assertTrue(messages.get(0) instanceof UserMessage);
        assertEquals("Hello", messages.get(0).getText());
        assertTrue(messages.get(1) instanceof AssistantMessage);
        assertEquals("Hi there", messages.get(1).getText());
    }
}
