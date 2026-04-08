package com.genchat.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 文件处理状态枚举
 */
@Getter
public enum FileStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    SUCCESS("success"),
    FAILED("failed");

    @EnumValue
    private final String value;

    FileStatus(String value) {
        this.value = value;
    }
}
