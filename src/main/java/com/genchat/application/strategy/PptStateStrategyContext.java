package com.genchat.application.strategy;

import com.genchat.dto.AiPptInst;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiPptInstService;
import com.genchat.service.AiPptTemplateService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Getter
@Setter
public class PptStateStrategyContext {
    private final AiPptInstService pptInstService;
    private final ChatMemory chatMemory;
    private final ChatClient chatClient;
    private final AgentTaskService agentTaskService;
    private final AiPptTemplateService pptTemplateService;
    private String modifyQuestion;
    private boolean modifyMode;


    public PptStateStrategyContext(AiPptInstService pptInstService,
                                   ChatMemory chatMemory,
                                   ChatClient client,
                                   AgentTaskService agentTaskService,
                                   AiPptTemplateService pptTemplateService) {
        this.pptInstService = pptInstService;
        this.chatMemory = chatMemory;
        this.chatClient = client;
        this.agentTaskService = agentTaskService;
        this.pptTemplateService = pptTemplateService;
    }

    public void loadChatHistory(String conversationId, List<Message> messages, boolean skipSystem, boolean addLabel) {
        if (!ObjectUtils.isEmpty(conversationId) && !ObjectUtils.isEmpty(chatMemory)) {
            var history = chatMemory.get(conversationId);
            if (!ObjectUtils.isEmpty(history)) {
                if (addLabel) {
                    messages.add(new UserMessage("Conversation history:"));
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

    public void setDisposable(String conversationId, Disposable disposable) {
        agentTaskService.setDisposable(conversationId, disposable);
    }

    public boolean shouldContinueToNextStep(String response) {
        if (!StringUtils.hasLength(response)) {
            return false;
        }
        var trimmedResponse = response.trim();
        if (trimmedResponse.contains("[START PPT GENERATION]") || trimmedResponse.contains("[START PPT GENERATION]".toLowerCase())) {
            return true;
        }
        if (trimmedResponse.contains("[PAUSE PPT GENERATION]") ||  trimmedResponse.contains("[PAUSE PPT GENERATION]".toLowerCase())) {
            return false;
        }

        return true;
    }

    public void continueStateMachine(AiPptInst inst,
                                     Sinks.Many<String> sink,
                                     String question,
                                     StringBuilder thinkingBuffer) {
        PptStateStrategyFactory.getInstance().executeNextState(inst, sink, question, thinkingBuffer, this);
    }
}
