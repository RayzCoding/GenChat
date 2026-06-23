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
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    private final AtomicReference<String> bucketHolder = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        bucketHolder.set(null);
        when(redissonClient.getTopic(eq("agent:stop"), eq(StringCodec.INSTANCE))).thenReturn(stopTopic);
        when(redissonClient.getBucket(anyString(), eq(StringCodec.INSTANCE))).thenReturn(taskBucket);
        when(taskBucket.trySet(anyString(), anyLong(), eq(TimeUnit.MINUTES))).thenAnswer(invocation -> {
            bucketHolder.set(invocation.getArgument(0));
            return true;
        });
        when(taskBucket.get()).thenAnswer(invocation -> bucketHolder.get());
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
        var sink = newSink();
        var result = agentTaskService.registerTask("conv-2", sink, "webSearchReactAgent");

        assertNotNull(result);
        assertTrue(agentTaskService.hasRunningTask("conv-2"));
    }

    @Test
    void registerTaskRejectsDuplicateLocalTask() {
        var sink = newSink();
        agentTaskService.registerTask("conv-3", sink, "webSearchReactAgent");

        assertNull(agentTaskService.registerTask("conv-3", sink, "webSearchReactAgent"));
    }

    @Test
    void stopTaskReturnsFalseWhenNoLocalTask() {
        assertFalse(agentTaskService.stopTask("missing-conversation"));
        verify(stopTopic, never()).publish(anyString());
    }

    @Test
    void stopLocalTaskEmitsStopMessageRemovesTaskAndReturnsFalse() {
        var sink = newSink();
        var messages = collectSinkMessages(sink);
        agentTaskService.registerTask("conv-stop", sink, "webSearchReactAgent");

        assertFalse(agentTaskService.stopTask("conv-stop"));
        assertFalse(agentTaskService.hasRunningTask("conv-stop"));
        assertTrue(messages.stream().anyMatch(line -> line.contains("stopped generating")));
        verify(taskBucket).delete();
        verify(stopTopic, never()).publish(anyString());
    }

    @Test
    void stopLocalTaskDisposesRegisteredDisposable() {
        var sink = newSink();
        agentTaskService.registerTask("conv-dispose", sink, "webSearchReactAgent");

        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);
        agentTaskService.setDisposable("conv-dispose", disposable);

        agentTaskService.stopTask("conv-dispose");

        verify(disposable).dispose();
    }

    @Test
    void stopRemoteTaskPublishesWhenOnlyRedisLockExists() {
        when(taskBucket.isExists()).thenReturn(true);
        when(taskBucket.get()).thenReturn("other-instance");
        when(stopTopic.publish("conv-remote")).thenReturn(1L);

        assertTrue(agentTaskService.stopTask("conv-remote"));

        verify(stopTopic).publish("conv-remote");
    }

    @Test
    void hasRunningTaskReturnsTrueWhenRedisBucketExists() {
        when(taskBucket.isExists()).thenReturn(true);

        assertTrue(agentTaskService.hasRunningTask("conv-redis"));
    }

    private static List<String> collectSinkMessages(Sinks.Many<String> sink) {
        List<String> messages = new ArrayList<>();
        sink.asFlux().subscribe(messages::add);
        return messages;
    }
}
