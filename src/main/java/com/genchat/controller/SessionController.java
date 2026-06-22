package com.genchat.controller;

import com.genchat.common.ResourceNotFoundException;
import com.genchat.common.Result;
import com.genchat.dto.PageResult;
import com.genchat.dto.SessionDetailDTO;
import com.genchat.dto.SessionSummaryDTO;
import com.genchat.service.AiChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    public Result<PageResult<SessionSummaryDTO>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("List sessions, page: {}, pageSize: {}", page, pageSize);
        return Result.success(sessionService.listSessions(page, pageSize));
    }

    @GetMapping("/search")
    public Result<PageResult<SessionSummaryDTO>> searchSessions(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("Search sessions, q: {}", q);
        if (!StringUtils.hasLength(q)) {
            return Result.success(sessionService.listSessions(page, pageSize));
        }
        return Result.success(sessionService.searchSessions(q.trim(), page, pageSize));
    }

    @GetMapping("/{conversationId}")
    public Result<SessionDetailDTO> getSessionDetail(@PathVariable String conversationId) {
        log.info("Get session detail, conversationId: {}", conversationId);
        return sessionService.getSessionDetail(conversationId)
                .map(Result::success)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session not found: " + conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public Result<Void> deleteSession(@PathVariable String conversationId) {
        log.info("Delete session, conversationId: {}", conversationId);
        if (!sessionService.deleteSession(conversationId)) {
            throw new ResourceNotFoundException(
                    "Session not found: " + conversationId);
        }
        return Result.success();
    }
}
