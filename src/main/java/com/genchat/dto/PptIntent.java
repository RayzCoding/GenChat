package com.genchat.dto;

import lombok.Getter;

/**
 * PPT intent enum
 */
@Getter
public enum PptIntent {

    /**
     * Create new PPT
     */
    CREATE_PPT("CREATE_PPT", "Create PPT"),
    /**
     * Modify existing PPT
     */
    MODIFY_PPT("MODIFY_PPT", "Modify PPT"),
    /**
     * Resume from breakpoint (continue a previously failed task)
     */
    RESUME_PPT("RESUME_PPT", "Resume from breakpoint");

    private final String code;
    private final String desc;

    PptIntent(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * Get enum by code
     */
    public static PptIntent fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (PptIntent intent : values()) {
            if (intent.code.equals(code)) {
                return intent;
            }
        }
        return null;
    }
}
