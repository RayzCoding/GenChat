package com.genchat.application.agent;

import com.genchat.config.GenChatProperties;
import com.genchat.dto.StopAgentResponse;
import com.genchat.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentFacade {

    private final AgentFactory agentFactory;
    private final AgentTaskService agentTaskService;
    private final GenChatProperties genChatProperties;

    public Flux<String> chatStream(String conversationId, String question) {
        return streamWithMemory(conversationId, "chat stream", () -> agentFactory.createWebSearchAgent(),
                agent -> agent.stream(conversationId, question));
    }

    public Flux<String> deepStream(String question, String conversationsId) {
        return streamWithMemory(conversationsId, "deep stream", agentFactory::createDeepResearchAgent,
                agent -> agent.stream(conversationsId, question));
    }

    public Flux<String> simpleStream(String question) {
        return streamSafely("simple stream", () -> agentFactory.createSimpleReactAgent().stream(question));
    }

    public Flux<String> fileStream(String conversationId, String question, String fileId) {
        return streamWithMemory(conversationId, "file stream", agentFactory::createFileReactAgent,
                agent -> agent.stream(conversationId, question, fileId));
    }

    public Flux<String> pptStream(String conversationsId, String question) {
        return streamWithMemory(conversationsId, "ppt stream", agentFactory::createPptBuilderAgent,
                agent -> agent.stream(conversationsId, question));
    }

    public Flux<String> skillsStream(String conversationsId, String question, String fileId) {
        return streamWithMemory(conversationsId, "skills stream", agentFactory::createSkillsReactAgent,
                agent -> agent.stream(conversationsId, question, fileId));
    }

    public StopAgentResponse stopAgent(String conversationId) {
        log.info("received request to stop agent, conversationId: {}", conversationId);

        if (ObjectUtils.isEmpty(conversationId)) {
            log.warn("conversationId is null or empty");
            return new StopAgentResponse(false, "There is no conversation task.");
        }
        var success = agentTaskService.stopTask(conversationId);
        if (!success) {
            log.warn("stop agent failed, conversationId: {}", conversationId);
            return new StopAgentResponse(false, "No task being found or stopped.");
        }
        return new StopAgentResponse(true, "Execution has been discontinued.");
    }

    private <T extends PersistentChatAgent> Flux<String> streamWithMemory(
            String conversationId,
            String context,
            Supplier<T> agentSupplier,
            AgentStream<T> streamFn) {
        return streamSafely(context, () -> {
            T agent = agentSupplier.get();
            agent.initPersistentChatMemory(conversationId, genChatProperties.getAgent().getChatMemorySize());
            return streamFn.apply(agent);
        });
    }

    private Flux<String> streamSafely(String context, Supplier<Flux<String>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.error("Error occurred while processing {}", context, e);
            return Flux.error(e);
        }
    }

    @FunctionalInterface
    private interface AgentStream<T extends PersistentChatAgent> {
        Flux<String> apply(T agent);
    }
}
