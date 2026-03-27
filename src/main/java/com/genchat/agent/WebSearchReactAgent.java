package com.genchat.agent;

import com.genchat.dto.AiChatSession;
import com.genchat.service.AiChatSessionService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Setter
public class WebSearchReactAgent {
    private final ChatModel chatModel;
    private ChatMemory chatMemory;
    private ChatClient chatClient;
    private AiChatSessionService sessionService;
    private int maxRounds;
    protected Long currentSessionId;

    public WebSearchReactAgent(ChatModel chatModel, AiChatSessionService sessionService, int maxRounds) {
        this.chatModel = chatModel;
        this.sessionService = sessionService;
        this.maxRounds = maxRounds;
        this.chatClient = ChatClient.builder(this.chatModel).build();
    }

    public Flux<String> stream(String conversationId, String question) {
        //TODO task manage
        //loading history
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        boolean skipSystem = true;
        boolean addLabel = true;
        loadChatHistory(conversationId, messages, skipSystem, addLabel);

        messages.add(new UserMessage("<question>" + question + "</question>"));
        // save current conversation message to database
        var aiChatSession = sessionService.saveQuestion(AiChatSession.builder()
                .question(question)
                .sessionId(conversationId).build()
        );
        currentSessionId= aiChatSession.getId();
        //TODO add Round
        //TODO add tool
        //TODO update current conversation answer
        //TODO return result
        return chatClient.prompt().messages(messages).stream().content();
    }

    private void loadChatHistory(String conversationId, List<Message> messages, boolean skipSystem, boolean addLabel) {
        if (!ObjectUtils.isEmpty(conversationId)&& !ObjectUtils.isEmpty(chatMemory)) {
            var history = chatMemory.get(conversationId);
            if (!ObjectUtils.isEmpty(history)) {
                if (addLabel) {
                    messages.add(new UserMessage("Conversation history："));
                }
                for (Message msg : history) {
                    if (skipSystem && msg instanceof SystemMessage) {
                        continue;
                    }
                    messages.add(msg);
                }
            }

        }
    }

    public void initPersistentChatMemory(String conversationId) {
        int maxMessages = 30;
        var historyMessages = sessionService.queryRecentBySessionId(conversationId, maxMessages);
        var chatMemory = MessageWindowChatMemory.builder().maxMessages(maxMessages).build();
        if (!CollectionUtils.isEmpty(historyMessages)) {
            historyMessages.forEach(message -> {
                var userQuestion = message.getQuestion();
                var systemAnswer = message.getAnswer();
                if (!ObjectUtils.isEmpty(userQuestion)){
                    chatMemory.add(conversationId, new UserMessage(userQuestion));
                }
                if (!ObjectUtils.isEmpty(systemAnswer)){
                    chatMemory.add(conversationId, new AssistantMessage(systemAnswer));
                }
            });
            log.info("Loading history messages, conversationId: {}, recordCount: {}", conversationId, historyMessages.size());
        }
        this.chatMemory = chatMemory;
    }
}
