package com.genchat.application.strategy;

import com.genchat.dto.AiPptInst;
import com.genchat.service.AgentTaskService;
import com.genchat.service.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.List;

@Slf4j
@Getter
@Setter
public class PptStateStrategyContext {

    private final PptStrategyDependencies dependencies;
    private final ChatMemory chatMemory;
    private final ChatClient chatClient;
    private final List<ToolCallback> tools;
    private final Long currentSessionId;
    private String modifyQuestion;
    private boolean modifyMode;

    private PptStateStrategyContext(PptStrategyDependencies dependencies,
                                    ChatMemory chatMemory,
                                    ChatClient chatClient,
                                    List<ToolCallback> tools,
                                    Long currentSessionId) {
        this.dependencies = dependencies;
        this.chatMemory = chatMemory;
        this.chatClient = chatClient;
        this.tools = tools;
        this.currentSessionId = currentSessionId;
    }

    public static PptStateStrategyContext forSession(PptStrategyDependencies dependencies,
                                                     ChatMemory chatMemory,
                                                     ChatClient chatClient,
                                                     List<ToolCallback> tools,
                                                     Long currentSessionId) {
        return new PptStateStrategyContext(dependencies, chatMemory, chatClient, tools, currentSessionId);
    }

    public PptStateStrategyFactory getStrategyFactory() {
        return dependencies.strategyFactory();
    }

    public AiPptInstService getPptInstService() {
        return dependencies.pptInstService();
    }

    public ChatModel getChatModel() {
        return dependencies.chatModel();
    }

    public AgentTaskService getAgentTaskService() {
        return dependencies.agentTaskService();
    }

    public AiPptTemplateService getPptTemplateService() {
        return dependencies.pptTemplateService();
    }

    public AiChatSessionService getSessionService() {
        return dependencies.sessionService();
    }

    public MinioService getMinioService() {
        return dependencies.minioService();
    }

    public ImageGenerationService getImageGenerationService() {
        return dependencies.imageGenerationService();
    }

    public PptPythonRenderService getPptPythonRenderService() {
        return dependencies.pptPythonRenderService();
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
        dependencies.agentTaskService().setDisposable(conversationId, disposable);
    }

    public boolean shouldContinueToNextStep(String response) {
        if (!StringUtils.hasLength(response)) {
            return false;
        }
        var trimmedResponse = response.trim();
        if (trimmedResponse.contains("[START PPT GENERATION]") || trimmedResponse.contains("[START PPT GENERATION]".toLowerCase())) {
            return true;
        }
        if (trimmedResponse.contains("[PAUSE PPT GENERATION]") || trimmedResponse.contains("[PAUSE PPT GENERATION]".toLowerCase())) {
            return false;
        }
        return true;
    }

    public void continueStateMachine(AiPptInst inst,
                                     Sinks.Many<String> sink,
                                     String question,
                                     StringBuilder thinkingBuffer) {
        getStrategyFactory().executeNextState(inst, sink, question, thinkingBuffer, this);
    }
}
