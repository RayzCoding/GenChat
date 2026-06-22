package com.genchat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Single PPT slide data structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Slide {

    /**
     * Page type.
     */
    private String pageType;

    /**
     * Page description.
     */
    private String pageDesc;

    /**
     * Template page index (1-based template page number).
     */
    private Integer templatePageIndex;

    /**
     * Page data (field name -> field data).
     */
    private Map<String, FieldData> data;
}
