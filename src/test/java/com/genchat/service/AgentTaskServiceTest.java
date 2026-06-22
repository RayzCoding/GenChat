package com.genchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import reactor.core.publisher.Sinks;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentTaskServiceTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    @SuppressWarnings("rawtypes")
    private RBucket taskBucket;
    @Mock
    private RTopic stopTopic;

    private AgentTaskService agentTaskService;

    @BeforeEach
    void setUp() {
        when(redissonClient.getTopic(eq("agent:stop"), eq(StringCodec.INSTANCE))).thenReturn(stopTopic);
        when(redissonClient.getBucket(anyString(), eq(StringCodec.INSTANCE))).thenReturn(taskBucket);
        agentTaskService = new AgentTaskService(redissonClient);
    }

    @Test
    void registerTaskReturnsNullForNullConversationId() {
        assertNull(agentTaskService.registerTask(null, newSink(), "agent"));
    }

    private static Sinks.Many<String> newSink() {
        return Sinks.many().unicast().<String>onBackpressureBuffer();
    }

    @Test
    void registerTaskReturnsNullWhenBucketLockNotAcquired() {
        when(taskBucket.trySet(anyString(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(false);

        var sink = newSink();
        var result = agentTaskService.registerTask("conv-1", sink, "webSearchReactAgent");

        assertNull(result);
    }

    @Test
    void registerTaskSucceedsWhenBucketLockAcquired() {
        when(taskBucket.trySet(anyString(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(true);

        var sink = newSink();
        var result = agentTaskService.registerTask("conv-2", sink, "webSearchReactAgent");

        assertNotNull(result);
        assertTrue(agentTaskService.hasRunningTask("conv-2"));
    }

    @Test
    void registerTaskRejectsDuplicateLocalTask() {
        when(taskBucket.trySet(anyString(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(true);
        var sink = newSink();
        agentTaskService.registerTask("conv-3", sink, "webSearchReactAgent");

        assertNull(agentTaskService.registerTask("conv-3", sink, "webSearchReactAgent"));
    }

    @Test
    void stopTaskReturnsFalseWhenNoLocalTask() {
        assertFalse(agentTaskService.stopTask("missing-conversation"));
        verify(stopTopic, never()).publish(anyString());
    }
}
