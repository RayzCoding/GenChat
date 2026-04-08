package com.genchat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * File information entity
 */
@Data
@TableName("file_info")
public class FileInfoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** File name */
    private String name;

    /** File path */
    private String path;

    /** File type (e.g. pdf, docx, png) */
    private String fileType;

    /** File size in bytes */
    private Long size;

    /** Extracted plain text content */
    private String extractedText;

    /** Whether the file has been vectorized */
    private Boolean embed;

    /** File status */
    private FileStatus status;

    /** Create time */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** Update time */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
