package com.genchat.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI PPT template DTO
 */
@Getter
@Setter
@Builder
public class AiPptTemplate {
    private Long id;
    private String templateCode;
    private String templateName;
    private String templateDesc;
    private String templateSchema;
    private String filePath;
    private String styleTags;
    private Integer slideCount;
    private LocalDateTime createTime;
}
