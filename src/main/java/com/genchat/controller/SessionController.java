package com.genchat.controller;

import com.genchat.agent.WebSearchReactAgent;
import com.genchat.dto.PageResult;
import com.genchat.dto.SessionDetailDTO;
import com.genchat.dto.SessionSummaryDTO;
import com.genchat.service.AiChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    private final AiChatSessionService sessionService;

    @GetMapping
    public PageResult<SessionSummaryDTO> listSessions(
            @RequestParam(defaultValue = WebSearchReactAgent.AGENT_TYPE) String agentType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("List sessions, agentType: {}, page: {}, pageSize: {}", agentType, page, pageSize);
        return sessionService.listSessions(agentType, page, pageSize);
    }

    @GetMapping("/search")
    public PageResult<SessionSummaryDTO> searchSessions(
            @RequestParam String q,
            @RequestParam(defaultValue = WebSearchReactAgent.AGENT_TYPE) String agentType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("Search sessions, q: {}, agentType: {}", q, agentType);
        if (!StringUtils.hasLength(q)) {
            return sessionService.listSessions(agentType, page, pageSize);
        }
        return sessionService.searchSessions(agentType, q.trim(), page, pageSize);
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<SessionDetailDTO> getSessionDetail(@PathVariable String conversationId) {
        log.info("Get session detail, conversationId: {}", conversationId);
        return sessionService.getSessionDetail(conversationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
