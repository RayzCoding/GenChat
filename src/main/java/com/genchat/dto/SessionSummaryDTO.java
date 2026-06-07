package com.genchat.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionSummaryDTO {
    private String conversationId;
    private String title;
    private String lastQuestion;
    private Integer messageCount;
    private String agentType;
    private LocalDateTime updatedAt;
}
