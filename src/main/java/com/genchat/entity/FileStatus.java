package com.genchat.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
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
    @JsonValue
    private final String value;

    FileStatus(String value) {
        this.value = value;
    }
}
