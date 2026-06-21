package com.genchat.controller;

import com.genchat.application.agent.AgentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentController {

    private final AgentFacade agentFacade;

    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chat(@RequestParam String conversationId,
                             @RequestParam String question) {
        log.info("received request to chat stream, conversationId: {}, question: {}",
                conversationId, question);
        if (ObjectUtils.isEmpty(conversationId) || ObjectUtils.isEmpty(question)) {
            log.warn("conversationId or question is null or empty");
            return Flux.error(new IllegalArgumentException("conversationId or question is null or empty"));
        }
        return agentFacade.chatStream(conversationId, question);
    }

    @GetMapping("/deep/stream")
    public Flux<String> deepStream(@RequestParam String question,
                                   @RequestParam String conversationsId) {
        log.info("Receive question, question: {}, conversationsId: {} ", question, conversationsId);
        if (!StringUtils.hasLength(question)) {
            log.warn("question is null or empty");
            return Flux.error(new IllegalArgumentException("question is null or empty"));
        }
        if (ObjectUtils.isEmpty(conversationsId)) {
            log.warn("conversationsId is null or empty");
            return Flux.error(new IllegalArgumentException("conversationsId is null or empty"));
        }
        return agentFacade.deepStream(question, conversationsId);
    }

    @GetMapping(value = "/simple/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> simpleChat(@RequestParam String question) {
        log.info("received request to simple chat stream,  question: {}", question);
        if (ObjectUtils.isEmpty(question)) {
            log.warn("Question is null or empty");
            return Flux.error(new IllegalArgumentException("Question is null or empty"));
        }
        return agentFacade.simpleStream(question);
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
        return agentFacade.fileStream(conversationId, question, fileId);
    }

    @GetMapping("/ppt/stream")
    public Flux<String> pptStream(@RequestParam String question,
                                  @RequestParam String conversationsId) {
        log.info("Received  ppt question, question: {}, conversationId: {}", question, conversationsId);
        if (!StringUtils.hasLength(question)) {
            log.warn("Question is null or empty");
            return Flux.error(new IllegalArgumentException("Question is null or empty"));
        }
        return agentFacade.pptStream(conversationsId, question);
    }

    @GetMapping("/skills/stream")
    public Flux<String> skillsStream(@RequestParam String question,
                                     @RequestParam String conversationsId,
                                     @RequestParam(required = false) String fileId) {
        log.info("Received skills question, question: {}, conversationId: {}", question, conversationsId);
        if (!StringUtils.hasLength(question)) {
            log.warn("Question is null or empty");
            return Flux.error(new IllegalArgumentException("Question is null or empty"));
        }
        return agentFacade.skillsStream(conversationsId, question, fileId);
    }

    @GetMapping("/stop")
    public Map<String, Object> stopAgent(@RequestParam String conversationId) {
        return agentFacade.stopAgent(conversationId);
    }
}
