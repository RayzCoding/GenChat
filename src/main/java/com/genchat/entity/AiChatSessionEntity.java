package com.genchat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI聊天会话实体
 * 存储agent与用户之间的对话历史，支持会话隔离和记忆功能
 */
@Data
@TableName("ai_chat_session")
public class AiChatSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 用户问题 */
    private String question;

    /** AI回答 */
    private String answer;

    /** 工具名称 */
    private String tools;

    /** 首次响应时间(ms) */
    private Long firstResponseTime;

    /** 完整响应时间(ms) */
    private Long totalResponseTime;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 引用链接 */
    private String reference;

    /** Agent类型 */
    private String agentType;

    /** 思考过程 */
    private String thinking;

    /** 文件ID */
    private String fileid;

    /** 推荐问题 */
    private String recommend;
}
