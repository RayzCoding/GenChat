package com.genchat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI PPT template entity
 */
@Data
@TableName("ai_ppt_template")
public class AiPptTemplateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Unique template code */
    private String templateCode;

    /** Template name */
    private String templateName;

    /** Template description */
    private String templateDesc;

    /** Template structure JSON */
    private String templateSchema;

    /** PPT template file path */
    private String filePath;

    /** Style tags: tech, business, minimal */
    private String styleTags;

    /** Number of slides in template */
    private Integer slideCount;

    /** Create time */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
