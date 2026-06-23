package com.genchat.agent;

import com.genchat.agent.core.AbstractReactAgent;
import com.genchat.agent.core.ReactStreamRequest;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.dto.AiChatSession;
import com.genchat.entity.AgentState;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Arrays;
import java.util.List;

public class SkillsReactAgent extends AbstractReactAgent {

    public static final String AGENT_TYPE = "skillsReactAgent";

    public SkillsReactAgent(ChatModel chatModel,
                            AiChatSessionService sessionService,
                            AgentTaskService agentTaskService,
                            ToolCallback[] toolCallbacks,
                            int maxRounds) {
        super(chatModel, sessionService, agentTaskService, Arrays.asList(toolCallbacks));
        this.maxRounds = maxRounds;
    }

    @Override
    protected String getAgentType() {
        return AGENT_TYPE;
    }

    @Override
    protected String getPrimarySystemPrompt() {
        return ReactAgentPrompts.getSkillsPrompt();
    }

    @Override
    protected AiChatSession buildSessionForSave(ReactStreamRequest request) {
        return AiChatSession.builder()
                .question(request.question())
                .sessionId(request.conversationId())
                .build();
    }

    @Override
    protected void appendExtraUserMessages(List<Message> messages, ReactStreamRequest request) {
        if (StringUtils.hasLength(request.fileId())) {
            messages.add(new UserMessage("<file>" + request.fileId() + "</file>"));
        }
    }

    @Override
    protected AgentState createAgentState() {
        return null;
    }

    @Override
    protected void onNonToolFinish(String finalText,
                                   Sinks.Many<String> sink,
                                   String conversationId,
                                   AgentState agentState) {
        // Skills mode does not emit reference or recommendations
    }

    @Override
    protected void onForceFinalComplete(String finalText,
                                        Sinks.Many<String> sink,
                                        String conversationId,
                                        AgentState agentState) {
        // Skills mode does not emit reference or recommendations on force final
    }

    @Override
    protected String getForceFinalSystemPrompt() {
        return ReactAgentPrompts.getFilePrompt();
    }

    public Flux<String> stream(String conversationId, String question, String fileId) {
        return streamInternal(ReactStreamRequest.withFile(conversationId, question, fileId));
    }
}
