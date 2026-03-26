package com.genchat.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genchat.entity.ChatMessage;
import com.genchat.mapper.ChatMessageMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ChatService extends ServiceImpl<ChatMessageMapper, ChatMessage> {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String chat(String userMessage) {
        String sessionId = UUID.randomUUID().toString();

        // 保存用户消息
        saveMessage(sessionId, "user", userMessage);

        // 调用AI
        String reply = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        // 保存AI回复
        saveMessage(sessionId, "assistant", reply);

        return reply;
    }

    private void saveMessage(String sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        save(message);
    }
}
