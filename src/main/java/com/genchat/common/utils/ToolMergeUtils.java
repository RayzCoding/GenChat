package com.genchat.common.utils;

import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

public final class ToolMergeUtils {

    private ToolMergeUtils() {
    }

    @SafeVarargs
    public static ToolCallback[] mergeTools(ToolCallback[]... toolArrays) {
        List<ToolCallback> result = new ArrayList<>();
        if (toolArrays != null) {
            for (ToolCallback[] array : toolArrays) {
                if (array != null) {
                    result.addAll(List.of(array));
                }
            }
        }
        return result.toArray(new ToolCallback[0]);
    }
}
