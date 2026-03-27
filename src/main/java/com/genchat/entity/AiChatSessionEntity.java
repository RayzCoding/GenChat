package com.genchat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI chat session entity
 * Stores conversation history between agent and user, supports session isolation and memory
 */
@Data
@TableName("ai_chat_session")
public class AiChatSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Session ID */
    private String sessionId;

    /** User question */
    private String question;

    /** AI answer */
    private String answer;

    /** Tool name */
    private String tools;

    /** First response time (ms) */
    private Long firstResponseTime;

    /** Total response time (ms) */
    private Long totalResponseTime;

    /** Create time */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** Update time */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** Reference link */
    private String reference;

    /** Agent type */
    private String agentType;

    /** Thinking process */
    private String thinking;

    /** File ID */
    private String fileid;

    /** Recommended questions */
    private String recommend;
}
