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
}
