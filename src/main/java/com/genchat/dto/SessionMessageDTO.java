package com.genchat.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SessionMessageDTO {
    private Long id;
    private String question;
    private String answer;
    private String thinking;
    private List<SearchResultDTO> reference;
    private List<String> recommend;
    private LocalDateTime createTime;
    private Long totalResponseTime;
}
