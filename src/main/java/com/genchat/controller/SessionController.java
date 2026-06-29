package com.genchat.controller;

import com.genchat.common.ResourceNotFoundException;
import com.genchat.common.Result;
import com.genchat.dto.PageResult;
import com.genchat.dto.SessionDetailDTO;
import com.genchat.dto.SessionSummaryDTO;
import com.genchat.service.SessionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    private final SessionQueryService sessionQueryService;

    @GetMapping
    public Result<PageResult<SessionSummaryDTO>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("List sessions, page: {}, pageSize: {}", page, pageSize);
        return Result.success(sessionQueryService.listSessions(page, pageSize));
    }

    @GetMapping("/search")
    public Result<PageResult<SessionSummaryDTO>> searchSessions(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("Search sessions, q: {}", q);
        if (!StringUtils.hasLength(q)) {
            return Result.success(sessionQueryService.listSessions(page, pageSize));
        }
        return Result.success(sessionQueryService.searchSessions(q.trim(), page, pageSize));
    }

    @GetMapping("/by-file/{fileId}")
    public Result<SessionDetailDTO> getSessionByFile(@PathVariable String fileId) {
        log.info("Get session by fileId: {}", fileId);
        var detail = sessionQueryService.getSessionDetailByFileId(fileId)
                .orElseGet(() -> SessionDetailDTO.builder()
                        .conversationId(null)
                        .messages(List.of())
                        .build());
        return Result.success(detail);
    }

    @GetMapping("/{conversationId}")
    public Result<SessionDetailDTO> getSessionDetail(@PathVariable String conversationId) {
        log.info("Get session detail, conversationId: {}", conversationId);
        return sessionQueryService.getSessionDetail(conversationId)
                .map(Result::success)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session not found: " + conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public Result<Void> deleteSession(@PathVariable String conversationId) {
        log.info("Delete session, conversationId: {}", conversationId);
        if (!sessionQueryService.deleteSession(conversationId)) {
            throw new ResourceNotFoundException(
                    "Session not found: " + conversationId);
        }
        return Result.success();
    }
}
