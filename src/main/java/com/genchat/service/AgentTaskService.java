package com.genchat.service;

import com.genchat.common.utils.JacksonJson;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AgentTaskService implements InitializingBean, DisposableBean {
    private final Map<String, TaskInfo> taskMap = new ConcurrentHashMap<>();
    private final RedissonClient redissonClient;

    private static final String TASK_KEY_PREFIX = "agent:task:";
    private static final String STOP_TOPIC_NAME = "agent:stop";
    private static final long TASK_TTL_MINUTES = 30;
    private static final long TTL_REFRESH_INTERVAL_MINUTES = 5;
    private final String instanceId;
    private final RTopic stopTopic;
    private int listenerId;

    public AgentTaskService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        this.stopTopic = redissonClient.getTopic(STOP_TOPIC_NAME, StringCodec.INSTANCE);
        log.info("AgentTaskManager init, instanceId:{}", instanceId);
    }

    private final ScheduledExecutorService ttlRefreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "agent-ttl-refresh");
        t.setDaemon(true);
        return t;
    });

    public boolean hasRunningTask(String conversationId) {
        if (taskMap.containsKey(conversationId)) return true;
        var taskBucket = getTaskBucket(conversationId);
        return taskBucket.isExists();
    }

    public TaskInfo registerTask(String conversationId, Sinks.Many<String> sink, String agentType) {
        if (Objects.isNull(conversationId)) {
            log.warn("ConversationId is null");
            return null;
        }
        var existTaskInfo = taskMap.get(conversationId);
        if (Objects.nonNull(existTaskInfo)) {
            log.warn("Conversation {} current exit task running, reject register new task.", conversationId);
            return null;
        }
        var taskBucket = getTaskBucket(conversationId);
        boolean acquired = taskBucket.trySet(instanceId, TASK_TTL_MINUTES, TimeUnit.MINUTES);
        if (!acquired) {
            var holder = taskBucket.get();
            log.info("Conversation [{}] has been acquired and holder{}, instance id{}.", conversationId, holder, instanceId);
            return null;
        }
        var taskInfo = new TaskInfo(sink, agentType);
        taskMap.put(conversationId, taskInfo);
        log.info("Conversation {} register new task, agent type is{}, instance id{}.", conversationId, agentType, instanceId);
        return taskInfo;
    }

    public boolean stopTask(String conversationId) {
        try {
            var localTaskInfo = taskMap.get(conversationId);
            if (!Objects.isNull(localTaskInfo)) {
                log.warn("Conversation {} has task being executed", conversationId);
                doStopTask(conversationId, localTaskInfo);
                return true;
            }
            var taskBucket = getTaskBucket(conversationId);
            if (!taskBucket.isExists()) {
                return false;
            }
            var holder = taskBucket.get();
            if (instanceId.equals(holder)) {
                log.debug("The task holder is this instance, skipping the broadcast, conversation id is {}.", conversationId);
                return false;
            }
            var receivers = stopTopic.publish(conversationId);
            log.info("Publish Stop Task, conversation id is {}, Number of subscribers:{}", conversationId, receivers);
            return true;
        } catch (Exception e) {
            log.warn("Conversation stop failed.", e);
            return false;
        }
    }

    private void doStopTask(String conversationId, TaskInfo taskInfo) {
        try {
            Disposable disposable = taskInfo.getDisposable();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
                log.info("The underlying call has been interrupted: conversationId={}", conversationId);
            }

            Sinks.Many<String> sink = taskInfo.getSink();
            if (sink != null) {
                try {
                    sink.tryEmitNext(createStopMessage());
                    sink.tryEmitComplete();
                    log.info("Stop message sent: conversationId={}", conversationId);
                } catch (Exception e) {
                    log.warn("Failed to send the stop message: conversationId={}", conversationId, e);
                }
            }
        } finally {
            doRemoveTask(conversationId);
        }
    }

    private String createStopMessage() {
        return JacksonJson.stopMessageJson("⏹ The user has stopped generating\n");
    }

    public void setDisposable(String conversationId, Disposable disposable) {
        var taskInfo = taskMap.get(conversationId);
        if (Objects.nonNull(taskInfo)) {
            taskInfo.setDisposable(disposable);
        }
    }

    private RBucket<String> getTaskBucket(String conversationId) {
        return redissonClient.getBucket(TASK_KEY_PREFIX + conversationId, StringCodec.INSTANCE);
    }

    @Override
    public void destroy() {
        try {
            stopTopic.removeListener(listenerId);
        } catch (Exception e) {
            log.warn("Failed to destroy stop task listener.", e);
        }
        ttlRefreshScheduler.shutdown();

        taskMap.keySet().forEach(this::doRemoveTask);
        log.info("AgentTaskManager Destruction complete, instanceId:{}", instanceId);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // Subscription stop messages
        listenerId = stopTopic.addListener(String.class, (channel, conversationId) -> {
            handleRemoteStop(conversationId);
        });

        // Start TTL refresh scheduled tasks
        ttlRefreshScheduler.scheduleAtFixedRate(
                this::refreshTaskTtls,
                TTL_REFRESH_INTERVAL_MINUTES,
                TTL_REFRESH_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
        log.info("AgentTaskManager Startup complete, subscribed to stop topic, TTL refresh interval: {}m", TTL_REFRESH_INTERVAL_MINUTES);
    }

    private void refreshTaskTtls() {
        if (taskMap.isEmpty()) {
            return;
        }

        log.debug("Start refreshing TTL, local task count: {}", taskMap.size());
        for (String conversationId : taskMap.keySet()) {
            try {
                RBucket<String> bucket = getTaskBucket(conversationId);
                String holder = bucket.get();
                if (instanceId.equals(holder)) {
                    bucket.expire(Duration.ofMinutes(TASK_TTL_MINUTES));
                } else {
                    taskMap.remove(conversationId);
                }
            } catch (Exception e) {
                log.error("TTL refresh failed: conversationId:{}", conversationId, e);
            }
        }
    }

    private void handleRemoteStop(String conversationId) {
        TaskInfo taskInfo = taskMap.remove(conversationId);
        if (taskInfo == null) {
            return;
        }
        doStopTask(conversationId, taskInfo);
    }

    private void doRemoveTask(String conversationId) {
        taskMap.remove(conversationId);

        RBucket<String> bucket = getTaskBucket(conversationId);
        String holder = bucket.get();
        if (instanceId.equals(holder)) {
            bucket.delete();
            log.debug("Delete Redis Task key: conversationId:{}", conversationId);
        }
    }

    @Getter
    public static class TaskInfo {
        private final Sinks.Many<String> sink;
        @Setter
        private Disposable disposable;
        private final long createTime;
        private final String agentType;

        public TaskInfo(Sinks.Many<String> sink, String agentType) {
            this.sink = sink;
            this.agentType = agentType;
            this.createTime = System.currentTimeMillis();
        }
    }
}
