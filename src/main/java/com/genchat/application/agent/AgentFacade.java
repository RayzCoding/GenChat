package com.genchat.application.agent;

import com.genchat.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentFacade {

    private final AgentFactory agentFactory;
    private final AgentTaskService agentTaskService;

    public Flux<String> chatStream(String conversationId, String question) {
        try {
            var agent = agentFactory.createWebSearchAgent();
            agent.initPersistentChatMemory(conversationId);
            return agent.stream(conversationId, question);
        } catch (Exception e) {
            log.error("error occurred while processing request to chat stream, error:", e);
            return Flux.error(e);
        }
    }

    public Flux<String> deepStream(String question, String conversationsId) {
        try {
            var agent = agentFactory.createDeepResearchAgent();
            agent.initPersistentChatMemory(conversationsId);
            return agent.stream(conversationsId, question);
        } catch (Exception e) {
            log.error("error occurred while processing deep stream, error:", e);
            return Flux.error(e);
        }
    }

    public Flux<String> simpleStream(String question) {
        try {
            return agentFactory.createSimpleReactAgent().stream(question);
        } catch (Exception e) {
            log.error("error occurred while processing request to chat stream, error:", e);
            return Flux.error(e);
        }
    }

    public Flux<String> fileStream(String conversationId, String question, String fileId) {
        try {
            var agent = agentFactory.createFileReactAgent();
            agent.initPersistentChatMemory(conversationId);
            return agent.stream(conversationId, question, fileId);
        } catch (Exception e) {
            log.error("error occurred while processing file stream, error:", e);
            return Flux.error(e);
        }
    }

    public Flux<String> pptStream(String conversationsId, String question) {
        try {
            var agent = agentFactory.createPptBuilderAgent();
            agent.initPersistentChatMemory(conversationsId);
            return agent.stream(conversationsId, question);
        } catch (Exception e) {
            log.error("Error occurred while processing ppt stream, error:", e);
            return Flux.error(e);
        }
    }

    public Flux<String> skillsStream(String conversationsId, String question, String fileId) {
        var agent = agentFactory.createSkillsReactAgent();
        agent.initPersistentChatMemory(conversationsId);
        return agent.stream(conversationsId, question, fileId);
    }

    public Map<String, Object> stopAgent(String conversationId) {
        log.info("received request to stop agent, conversationId: {}", conversationId);
        var result = new HashMap<String, Object>();

        if (ObjectUtils.isEmpty(conversationId)) {
            log.warn("conversationId is null or empty");
            result.put("success", false);
            result.put("message", "There is no conversation task.");
            return result;
        }
        var success = agentTaskService.stopTask(conversationId);
        if (!success) {
            log.warn("stop agent failed, conversationId: {}", conversationId);
            result.put("success", false);
            result.put("message", "No task being found or stopped.");
            return result;
        }
        result.put("success", true);
        result.put("message", "Execution has been discontinued.");
        return result;
    }
}
