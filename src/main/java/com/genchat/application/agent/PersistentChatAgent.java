package com.genchat.application.agent;

import reactor.core.publisher.Flux;

/**
 * Agents that restore conversation history before streaming.
 */
public interface PersistentChatAgent {

    void initPersistentChatMemory(String conversationId);
}
