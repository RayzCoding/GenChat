package com.genchat.agent;

import com.genchat.agent.core.AbstractReactAgent;
import com.genchat.agent.core.ReactStreamRequest;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.dto.AiChatSession;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

public class FileReactAgent extends AbstractReactAgent {

    public static final String AGENT_TYPE = "fileReactAgent";

    public FileReactAgent(ChatModel chatModel,
                          AiChatSessionService sessionService,
                          AgentTaskService agentTaskService,
                          List<ToolCallback> tools) {
        super(chatModel, sessionService, agentTaskService, tools);
    }

    @Override
    protected String getAgentType() {
        return AGENT_TYPE;
    }

    @Override
    protected String getPrimarySystemPrompt() {
        return ReactAgentPrompts.getFilePrompt();
    }

    @Override
    protected String getHistoryLabel() {
        return "Conversation history:";
    }

    @Override
    protected AiChatSession buildSessionForSave(ReactStreamRequest request) {
        return AiChatSession.builder()
                .question(request.question())
                .fileid(request.fileId())
                .sessionId(request.conversationId())
                .build();
    }

    @Override
    protected void appendExtraUserMessages(List<Message> messages, ReactStreamRequest request) {
        messages.add(new UserMessage("<fileid>" + request.fileId() + "</fileid>"));
    }

    @Override
    protected void emitToolThinking(String toolName, String argsJson, Sinks.Many<String> sink) {
        if (toolName.contains("loadContent")) {
            sink.tryEmitNext(new AgentStreamEvent.Thinking("📖 Retrieving the contents of the file, please wait... ").toJSON());
        }
    }

    public Flux<String> stream(String conversationId, String question, String fileId) {
        return streamInternal(ReactStreamRequest.withFile(conversationId, question, fileId));
    }
}
