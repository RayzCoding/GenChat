package com.genchat.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentTaskService {
    private final Map<String, TaskInfo> taskMap = new ConcurrentHashMap<>();

    public boolean hasRunningTask(String conversationId) {
        return taskMap.containsKey(conversationId);
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
        var taskInfo = new TaskInfo(sink, agentType);
        taskMap.put(conversationId, taskInfo);
        log.info("Conversation {} register new task.", conversationId);
        return taskInfo;
    }

    public void stopTask(String conversationId) {
        var taskInfo = taskMap.get(conversationId);
        if (Objects.isNull(taskInfo)) {
            log.warn("Conversation {} has no task being executed", conversationId);
        }

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
        taskMap.remove(conversationId);
    }

    private String createStopMessage() {
        JSONObject obj = new JSONObject();
        obj.put("type", "text");
        obj.put("content", "⏹ The user has stopped generating\n");
        return JSON.toJSONString(obj);
    }

    @Getter
    public static class TaskInfo {
        private final Sinks.Many<String> sink;
        @Setter
        private Disposable disposable;
        private final long createTime;
        private String agentType;

        public TaskInfo(Sinks.Many<String> sink, String agentType) {
            this.sink = sink;
            this.agentType = agentType;
            this.createTime = System.currentTimeMillis();
        }
    }
}
