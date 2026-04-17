package com.genchat.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI PPT generation instance DTO
 */
@Getter
@Setter
@Builder
public class AiPptInst {
    private Long id;
    private String conversationId;
    private String templateCode;
    private String status;
    private String query;
    private String requirement;
    private String searchInfo;
    private String outline;
    private String pptSchema;
    private String fileUrl;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
