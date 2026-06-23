package com.genchat.service.session;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.genchat.dto.SearchResultDTO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses persisted session payload fields (reference / recommend JSON).
 */
public final class SessionPayloadParser {

    private SessionPayloadParser() {
    }

    public static List<SearchResultDTO> parseReference(String referenceJson) {
        if (!StringUtils.hasLength(referenceJson)) {
            return Collections.emptyList();
        }
        try {
            var root = JSON.parseObject(referenceJson);
            if (root == null) {
                return Collections.emptyList();
            }
            Object content = root.get("content");
            if (content == null) {
                content = root.get("data");
            }
            if (content instanceof JSONArray array) {
                return parseSearchResultArray(array);
            }
            if (content instanceof String contentStr) {
                var parsedArray = JSON.parseArray(contentStr);
                if (parsedArray != null) {
                    return parseSearchResultArray(parsedArray);
                }
            }
            var directArray = JSON.parseArray(referenceJson);
            if (directArray != null) {
                return parseSearchResultArray(directArray);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Collections.emptyList();
    }

    public static List<String> parseRecommend(String recommendJson) {
        if (!StringUtils.hasLength(recommendJson)) {
            return Collections.emptyList();
        }
        try {
            var root = JSON.parseObject(recommendJson);
            if (root != null) {
                Object content = root.get("content");
                if (content instanceof JSONArray array) {
                    return array.toJavaList(String.class);
                }
                if (content instanceof String contentStr) {
                    var parsedArray = JSON.parseArray(contentStr);
                    if (parsedArray != null) {
                        return parsedArray.toJavaList(String.class);
                    }
                }
            }
            var directArray = JSON.parseArray(recommendJson);
            if (directArray != null) {
                return directArray.toJavaList(String.class);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Collections.emptyList();
    }

    private static List<SearchResultDTO> parseSearchResultArray(JSONArray array) {
        var results = new ArrayList<SearchResultDTO>();
        for (int i = 0; i < array.size(); i++) {
            var item = array.getJSONObject(i);
            if (item == null) {
                continue;
            }
            results.add(SearchResultDTO.builder()
                    .url(item.getString("url"))
                    .title(item.getString("title"))
                    .content(item.getString("content"))
                    .build());
        }
        return results;
    }
}
