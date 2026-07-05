package com.genchat.service.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.genchat.common.utils.JacksonJson;
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
            var root = JacksonJson.parseTreeLenient(referenceJson.trim());
            if (root == null) {
                return Collections.emptyList();
            }
            if (root.isArray()) {
                return parseSearchResultArray(root);
            }
            if (!root.isObject()) {
                return Collections.emptyList();
            }
            JsonNode content = root.get("content");
            if (content == null || content.isNull()) {
                content = root.get("data");
            }
            if (content != null && !content.isNull()) {
                if (content.isArray()) {
                    return parseSearchResultArray(content);
                }
                if (content.isTextual()) {
                    var parsedArray = JacksonJson.parseTreeLenient(content.asText());
                    if (parsedArray != null && parsedArray.isArray()) {
                        return parseSearchResultArray(parsedArray);
                    }
                }
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
            var root = JacksonJson.parseTreeLenient(recommendJson.trim());
            if (root == null) {
                return Collections.emptyList();
            }
            if (root.isArray()) {
                return parseStringArray(root);
            }
            if (root.isObject()) {
                JsonNode content = root.get("content");
                if (content != null && !content.isNull()) {
                    if (content.isArray()) {
                        return parseStringArray(content);
                    }
                    if (content.isTextual()) {
                        var parsedArray = JacksonJson.parseTreeLenient(content.asText());
                        if (parsedArray != null && parsedArray.isArray()) {
                            return parseStringArray(parsedArray);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Collections.emptyList();
    }

    private static List<SearchResultDTO> parseSearchResultArray(JsonNode array) {
        var results = new ArrayList<SearchResultDTO>();
        for (JsonNode item : array) {
            if (item == null || item.isNull()) {
                continue;
            }
            results.add(SearchResultDTO.builder()
                    .url(JacksonJson.getSafe(item, "url"))
                    .title(JacksonJson.getSafe(item, "title"))
                    .content(JacksonJson.getSafe(item, "content"))
                    .build());
        }
        return results;
    }

    private static List<String> parseStringArray(JsonNode array) {
        var results = new ArrayList<String>();
        for (JsonNode item : array) {
            if (item != null && !item.isNull()) {
                results.add(item.asText());
            }
        }
        return results;
    }
}
