package com.genchat.agent.core;

import com.genchat.agent.model.AgentState;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactToolSupportTest {

    @Test
    void findToolReturnsMatchingCallback() {
        ToolCallback search = mock(ToolCallback.class);
        when(search.getToolDefinition()).thenReturn(
                ToolDefinition.builder().name("search").description("search").inputSchema("{}").build());

        ToolCallback found = ReactToolSupport.findTool(List.of(search), "search");
        assertEquals(search, found);
    }

    @Test
    void findToolReturnsNullWhenMissing() {
        assertNull(ReactToolSupport.findTool(List.of(), "missing"));
    }

    @Test
    void parseTavilySearchResultExtractsUrls() {
        var state = new AgentState();
        var payload = """
                [{
                  "text": {
                    "results": [
                      {"url":"https://example.com","title":"Example","content":"Body"}
                    ]
                  }
                }]
                """;

        ReactToolSupport.parseTavilySearchResult(payload, state);

        assertEquals(1, state.searchResults.size());
        assertEquals("https://example.com", state.searchResults.getFirst().url());
    }
}
