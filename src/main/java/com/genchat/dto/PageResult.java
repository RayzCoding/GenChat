package com.genchat.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageResult<T> {
    private long total;
    private List<T> items;
}
