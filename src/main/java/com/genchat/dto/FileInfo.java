package com.genchat.dto;

import com.genchat.common.FileUtil;
import com.genchat.entity.FileStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

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

    public static FileInfo initFile(MultipartFile file) {
        return FileInfo.builder()
                .name(file.getOriginalFilename())
                .fileType(FileUtil.getFileType(file.getOriginalFilename()))
                .size(file.getSize())
                .status(FileStatus.PROCESSING)
                .build();
    }
}
