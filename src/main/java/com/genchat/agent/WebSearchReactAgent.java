package com.genchat.agent;

import com.alibaba.fastjson.JSON;
import com.genchat.agent.core.AbstractReactAgent;
import com.genchat.agent.core.ReactStreamRequest;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.dto.AiChatSession;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Arrays;
import java.util.List;

public class WebSearchReactAgent extends AbstractReactAgent {

    public static final String AGENT_TYPE = "webSearchReactAgent";

    public WebSearchReactAgent(ChatModel chatModel,
                               AiChatSessionService sessionService,
                               AgentTaskService agentTaskService,
                               ToolCallback[] webSearchToolCallbacks,
                               int maxRounds) {
        super(chatModel, sessionService, agentTaskService, Arrays.asList(webSearchToolCallbacks));
        this.maxRounds = maxRounds;
    }

    @Override
    protected String getAgentType() {
        return AGENT_TYPE;
    }

    @Override
    protected String getPrimarySystemPrompt() {
        return ReactAgentPrompts.getWebSearchPrompt();
    }

    @Override
    protected AiChatSession buildSessionForSave(ReactStreamRequest request) {
        return AiChatSession.builder()
                .question(request.question())
                .sessionId(request.conversationId())
                .build();
    }

    @Override
    protected void emitToolThinking(String toolName, String argsJson, Sinks.Many<String> sink) {
        if (toolName.contains("search")) {
            var args = JSON.parseObject(argsJson);
            var query = args.getString("query");
            var queryThink = org.springframework.util.StringUtils.hasLength(query)
                    ? "🔍 Searching for information: " + query + "\n"
                    : "🔍 Searching for related information\n";
            sink.tryEmitNext(new AgentStreamEvent.Thinking(queryThink).toJSON());
        }
    }

    public Flux<String> stream(String conversationId, String question) {
        return streamInternal(ReactStreamRequest.of(conversationId, question));
    }
}
