package com.genchat.dto;

import com.genchat.agent.model.SearchResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * SimpleReactAgent execution result containing the final answer and search results.
 */
@Data
@Builder
@AllArgsConstructor
public class SimpleReactResult {
    /**
     * Final answer (plain text).
     */
    private String answer;

    /**
     * Search result list.
     */
    private List<SearchResult> searchResults;

    public List<SearchResult> getSearchResults() {
        return searchResults;
    }
}
