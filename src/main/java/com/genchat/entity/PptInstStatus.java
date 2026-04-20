package com.genchat.entity;

import lombok.Getter;

/**
 * PPT instance status enum
 */
@Getter
public enum PptInstStatus {

    /**
     * Initialization
     */
    INIT("INIT", "Initialization"),
    /**
     * Requirement clarification
     */
    REQUIREMENT("REQUIREMENT", "Requirement clarification"),
    /**
     * Information collection
     */
    SEARCH("SEARCH", "Information collection"),
    /**
     * Outline generation
     */
    OUTLINE("OUTLINE", "Outline generation"),
    /**
     * Template selection
     */
    TEMPLATE("TEMPLATE", "Template selection"),
    /**
     * Schema generation
     */
    SCHEMA("SCHEMA", "Schema generation"),
    /**
     * PPT rendering
     */
    RENDER("RENDER", "PPT rendering"),
    /**
     * Completed
     */
    SUCCESS("SUCCESS", "Completed"),
    /**
     * Failed
     */
    FAILED("FAILED", "Failed");

    private final String code;
    private final String desc;

    PptInstStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * Get enum by code
     */
    public static PptInstStatus fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (PptInstStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
