package com.genchat.dto;

import com.genchat.entity.FileStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * File information DTO
 */
@Getter
@Setter
@Builder
public class FileInfo {
    private Long id;
    private String name;
    private String path;
    private String fileType;
    private Long size;
    private String extractedText;
    @Builder.Default
    private Boolean embed = false;
    @Builder.Default
    private FileStatus status = FileStatus.PENDING;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
