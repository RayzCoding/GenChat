package com.genchat.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * File processing status enum
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
