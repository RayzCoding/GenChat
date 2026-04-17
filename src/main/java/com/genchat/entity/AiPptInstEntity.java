package com.genchat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI PPT generation instance entity
 */
@Data
@TableName("ai_ppt_inst")
public class AiPptInstEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Conversation ID */
    private String conversationId;

    /** Selected template code */
    private String templateCode;

    /** Status */
    private String status;

    /** User original query */
    private String query;

    /** Requirement clarification */
    private String requirement;

    /** Search information */
    private String searchInfo;

    /** Outline */
    private String outline;

    /** AI generated PPT schema JSON */
    private String pptSchema;

    /** Generated PPT file URL */
    private String fileUrl;

    /** Error message */
    private String errorMsg;

    /** Create time */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** Update time */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
