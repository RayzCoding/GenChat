package com.genchat.controller;

import com.genchat.application.agent.AgentFacade;
import com.genchat.application.validation.StreamRequestValidator;
import com.genchat.dto.StopAgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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
        var error = StreamRequestValidator.requireConversationAndQuestionFlux(conversationId, question);
        if (error != null) {
            return error;
        }
        return agentFacade.chatStream(conversationId, question);
    }

    @GetMapping("/deep/stream")
    public Flux<String> deepStream(@RequestParam String question,
                                   @RequestParam(required = false) String conversationsId,
                                   @RequestParam(required = false) String conversationId) {
        var effectiveConversationId = StreamRequestValidator.resolveConversationId(conversationId, conversationsId);
        log.info("Receive question, question: {}, conversationId: {}", question, effectiveConversationId);
        var questionError = StreamRequestValidator.requireQuestionFlux(question);
        if (questionError != null) {
            return questionError;
        }
        var conversationError = StreamRequestValidator.requireConversationIdFlux(effectiveConversationId);
        if (conversationError != null) {
            return conversationError;
        }
        return agentFacade.deepStream(question, effectiveConversationId);
    }

    @GetMapping(value = "/simple/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> simpleChat(@RequestParam String question) {
        log.info("received request to simple chat stream, question: {}", question);
        var error = StreamRequestValidator.requireQuestionFlux(question);
        if (error != null) {
            return error;
        }
        return agentFacade.simpleStream(question);
    }

    @GetMapping(value = "/file/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> fileStream(@RequestParam String question,
                                   @RequestParam String conversationId,
                                   @RequestParam String fileId) {
        log.info("Receive file question, question: {}, conversationId: {}, fileId: {}",
                question, conversationId, fileId);
        var error = StreamRequestValidator.requireFileStreamParamsFlux(conversationId, question, fileId);
        if (error != null) {
            return error;
        }
        return agentFacade.fileStream(conversationId, question, fileId);
    }

    @GetMapping("/ppt/stream")
    public Flux<String> pptStream(@RequestParam String question,
                                  @RequestParam(required = false) String conversationsId,
                                  @RequestParam(required = false) String conversationId) {
        var effectiveConversationId = StreamRequestValidator.resolveConversationId(conversationId, conversationsId);
        log.info("Received ppt question, question: {}, conversationId: {}", question, effectiveConversationId);
        var error = StreamRequestValidator.requireQuestionFlux(question);
        if (error != null) {
            return error;
        }
        return agentFacade.pptStream(effectiveConversationId, question);
    }

    @GetMapping("/skills/stream")
    public Flux<String> skillsStream(@RequestParam String question,
                                     @RequestParam(required = false) String conversationsId,
                                     @RequestParam(required = false) String conversationId,
                                     @RequestParam(required = false) String fileId) {
        var effectiveConversationId = StreamRequestValidator.resolveConversationId(conversationId, conversationsId);
        log.info("Received skills question, question: {}, conversationId: {}", question, effectiveConversationId);
        var error = StreamRequestValidator.requireQuestionFlux(question);
        if (error != null) {
            return error;
        }
        return agentFacade.skillsStream(effectiveConversationId, question, fileId);
    }

    @GetMapping("/stop")
    public StopAgentResponse stopAgent(@RequestParam String conversationId) {
        return agentFacade.stopAgent(conversationId);
    }
}
