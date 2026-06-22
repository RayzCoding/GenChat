package com.genchat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PPT field data structure for text/image/background field types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldData {

    /**
     * Field type: text/image/background
     */
    private String type;

    /**
     * Field content:
     * - text: text content
     * - image: image generation prompt
     * - background: background layout description
     */
    private String content;

    /**
     * Character limit (text type only).
     */
    private Integer fontLimit;

    /**
     * Image URL (image and background types only).
     */
    private String url;
}
