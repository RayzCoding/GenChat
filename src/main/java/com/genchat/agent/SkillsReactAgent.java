package com.genchat.agent;

import com.genchat.agent.core.AbstractReactAgent;
import com.genchat.agent.core.ReactStreamRequest;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.dto.AiChatSession;
import com.genchat.agent.model.AgentState;
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
                .fileid(resolveFileId(request))
                .build();
    }

    @Override
    protected void appendExtraUserMessages(List<Message> messages, ReactStreamRequest request) {
        var fileId = resolveFileId(request);
        if (StringUtils.hasLength(fileId)) {
            messages.add(new UserMessage("<fileid>" + fileId + "</fileid>"));
        }
    }

    @Override
    protected void emitToolThinking(String toolName, String argsJson, Sinks.Many<String> sink) {
        if (toolName.contains("loadFileContents") || toolName.contains("loadContent")) {
            sink.tryEmitNext(new AgentStreamEvent.Thinking("📖 Loading file contents, please wait...\n").toJSON());
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

    private String resolveFileId(ReactStreamRequest request) {
        if (StringUtils.hasLength(request.fileId())) {
            return request.fileId();
        }
        return sessionService.findLatestFileIdBySessionId(request.conversationId()).orElse(null);
    }
}
