package com.genchat.agent;

import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
public class DeepResearchAgent {
    private final AiChatSessionService sessionService;
    private final ChatModel chatModel;
    private final AgentTaskService agentTaskService;
    private final List<ToolCallback> webSearchToolCallbacks;
    private final ChatClient chatClient;
    private final int maxRounds;
    private ChatMemory chatMemory;
    protected String agentType;

    public DeepResearchAgent(AiChatSessionService sessionService,
                             ChatModel chatModel,
                             List<ToolCallback> webSearchToolCallbacks,
                             AgentTaskService agentTaskService,
                             int maxRounds) {
        this.agentType = "DeepResearchAgent";
        this.sessionService = sessionService;
        this.chatModel = chatModel;
        this.webSearchToolCallbacks = webSearchToolCallbacks;
        this.agentTaskService = agentTaskService;
        this.maxRounds = maxRounds;
        this.chatClient = ChatClient.builder(chatModel).build();
    }


    public void initPersistentChatMemory(String conversationId) {
        int maxMessages = 30;
        var historyMessages = sessionService.queryRecentBySessionId(conversationId, maxMessages);
        var chatMemory = MessageWindowChatMemory.builder().maxMessages(maxMessages).build();
        if (!CollectionUtils.isEmpty(historyMessages)) {
            historyMessages.forEach(message -> {
                var userQuestion = message.getQuestion();
                var systemAnswer = message.getAnswer();
                if (!ObjectUtils.isEmpty(userQuestion)) {
                    chatMemory.add(conversationId, new UserMessage(userQuestion));
                }
                if (!ObjectUtils.isEmpty(systemAnswer)) {
                    chatMemory.add(conversationId, new AssistantMessage(systemAnswer));
                }
            });
            log.info("Loading history messages, conversationId: {}, recordCount: {}", conversationId, historyMessages.size());
        }
        this.chatMemory = chatMemory;
    }

    public Flux<String> stream(String conversationsId, String question) {
        return null;
    }
}
