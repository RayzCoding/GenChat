package com.genchat.agent.core;

import com.genchat.service.AgentTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ReactRoundSchedulerTest {

    @Mock
    private AgentTaskService agentTaskService;

    @Test
    void shouldRetryStreamUsesMaxRetriesNotMaxRounds() {
        var noRetry = scheduler(5, 0);
        assertFalse(noRetry.shouldRetryStream(0));

        var limitedRetry = scheduler(5, 2);
        assertTrue(limitedRetry.shouldRetryStream(0));
        assertTrue(limitedRetry.shouldRetryStream(1));
        assertFalse(limitedRetry.shouldRetryStream(2));
    }

    private ReactRoundScheduler scheduler(int maxRounds, int maxRetries) {
        return new ReactRoundScheduler(
                null,
                List.of(),
                agentTaskService,
                maxRounds,
                maxRetries,
                Collections.synchronizedList(Collections.emptyList()),
                null,
                null,
                null);
    }
}
