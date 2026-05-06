package com.genchat.controller;

import com.genchat.agent.FileReactAgent;
import com.genchat.agent.PPTBuilderAgent;
import com.genchat.agent.SimpleReactAgent;
import com.genchat.agent.WebSearchReactAgent;
import com.genchat.application.tool.FileContentTool;
import com.genchat.config.WebSearchToolInitConfig;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import com.genchat.service.AiPptInstService;
import com.genchat.service.AiPptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentController {

    private final ChatModel chatModel;
    private final AiChatSessionService sessionService;
    private final AgentTaskService agentTaskService;
    private final WebSearchToolInitConfig webSearchToolInitConfig;
    private final AiPptInstService aiPptInstService;
    private final AiPptTemplateService pptTemplateService;
    private final FileContentTool fileContentTool;

    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chat(@RequestParam String conversationId,
                             @RequestParam String question) {
        log.info("received request to chat stream, conversationId: {}, question: {}",
                conversationId, question);
        if (ObjectUtils.isEmpty(conversationId) || ObjectUtils.isEmpty(question)) {
            log.warn("conversationId or question is null or empty");
            return Flux.error(new IllegalArgumentException("conversationId or question is null or empty"));
        }

        try {
            var webSearchReactAgent = new WebSearchReactAgent(chatModel, sessionService,
                    agentTaskService, webSearchToolInitConfig.getWebSearchToolCallbacks(),
                    5);
            webSearchReactAgent.initPersistentChatMemory(conversationId);

            return webSearchReactAgent.stream(conversationId, question);
        } catch (Exception e) {
            log.error("error occurred while processing request to chat stream, error:", e);
            return Flux.error(e);
        }
    }

    @GetMapping(value = "/simple/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> simpleChat(@RequestParam String question) {
        log.info("received request to simple chat stream,  question: {}", question);
        if ( ObjectUtils.isEmpty(question)) {
            log.warn("Question is null or empty");
            return Flux.error(new IllegalArgumentException("Question is null or empty"));
        }

        try {
            var webSearchReactAgent = new SimpleReactAgent(chatModel, List.of(webSearchToolInitConfig.getWebSearchToolCallbacks()));
            return webSearchReactAgent.stream(question);
        } catch (Exception e) {
            log.error("error occurred while processing request to chat stream, error:", e);
            return Flux.error(e);
        }
    }

    @GetMapping("/file/stream")
    public Flux<String> fileStream(@RequestParam String question,
                                   @RequestParam String conversationId,
                                   @RequestParam String fileId) {
        log.info("Receive file question, question: {}, conversationId: {}, fileId: {}", question, conversationId, fileId);
        if (!StringUtils.hasLength(question)) {
            log.warn("question is null or empty");
            return Flux.error(new IllegalArgumentException("question is null or empty"));
        }
        if (ObjectUtils.isEmpty(conversationId) || ObjectUtils.isEmpty(fileId)) {
            log.warn("conversationId or question is null or empty");
            return Flux.error(new IllegalArgumentException("conversationId or question is null or empty"));
        }
        try {
            var fileReactAgent = new FileReactAgent(chatModel, sessionService,
                    agentTaskService, List.of(ToolCallbacks.from(fileContentTool)));
            fileReactAgent.initPersistentChatMemory(conversationId);

            return fileReactAgent.stream(conversationId, question, fileId);
        } catch (Exception e) {
            log.error("error occurred while processing file stream, error:", e);
            return Flux.error(e);
        }

    }

    @GetMapping("/ppt/stream")
    public Flux<String> pptStream(@RequestParam String question,
                                  @RequestParam String conversationsId) {
        log.info("Received  ppt question, question: {}, conversationId: {}", question, conversationsId);
        if (!StringUtils.hasLength(question)) {
            log.warn("Question is null or empty");
            return Flux.error(new IllegalArgumentException("Question is null or empty"));
        }
        try {
            var pptBuilderAgent = new PPTBuilderAgent(chatModel, sessionService,
                    agentTaskService, webSearchToolInitConfig.getWebSearchToolCallbacks(),
                    aiPptInstService, pptTemplateService, 5);
            pptBuilderAgent.initPersistentChatMemory(conversationsId);
            return pptBuilderAgent.stream(conversationsId, question);
        } catch (Exception e) {
            log.error("Error occurred while processing ppt stream, error:", e);
            return Flux.error(e);
        }
    }

    @GetMapping("/stop")
    public Map<String, Object> stopAgent(@RequestParam String conversationId) {
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
