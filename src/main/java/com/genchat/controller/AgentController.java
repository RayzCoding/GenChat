package com.genchat.controller;

import com.genchat.agent.WebSearchReactAgent;
import com.genchat.service.AiChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.ObjectUtils;
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

    private final ChatModel chatModel;
    private final AiChatSessionService sessionService;

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
            var webSearchReactAgent = new WebSearchReactAgent(chatModel, sessionService, 5);
            webSearchReactAgent.initPersistentChatMemory(conversationId);

            return webSearchReactAgent.stream(conversationId, question);
        } catch (Exception e) {
            log.error("error occurred while processing request to chat stream");
            return Flux.error(e);
        }
    }
}
