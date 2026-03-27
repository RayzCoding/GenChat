package com.genchat.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI聊天会话 DTO
 */
@Getter
@Builder
@Setter
public class AiChatSession {
    private Long id;
    private String sessionId;
    private String question;
    private String answer;
    private String tools;
    private Long firstResponseTime;
    private Long totalResponseTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String reference;
    private String agentType;
    private String thinking;
    private String fileid;
    private String recommend;
}
