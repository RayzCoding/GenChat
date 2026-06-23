package com.genchat.application.stream;

import com.genchat.service.AgentTaskService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Objects;
import java.util.function.Supplier;

@Slf4j
public final class AgentStreamLifecycle {

    private AgentStreamLifecycle() {
    }

    public record StreamBuffers(StringBuilder finalAnswer, StringBuilder thinking) {
        public static StreamBuffers create() {
            return new StreamBuffers(new StringBuilder(), new StringBuilder());
        }
    }

    public record StartedStream(Sinks.Many<String> sink, Flux<String> flux) {
    }

    public static boolean isConversationBusy(AgentTaskService agentTaskService, String conversationId) {
        return !Objects.isNull(conversationId) && agentTaskService.hasRunningTask(conversationId);
    }

    public static StartedStream startStream(AgentTaskService agentTaskService,
                                            String conversationId,
                                            String agentType) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        var taskInfo = agentTaskService.registerTask(conversationId, sink, agentType);
        if (Objects.isNull(taskInfo)) {
            return null;
        }
        return new StartedStream(sink, sink.asFlux());
    }

    /**
     * Attaches chunk accumulation, cancel cleanup, and finally persistence hooks to an agent SSE flux.
     */
    public static Flux<String> attach(Flux<String> source,
                                      String conversationId,
                                      AgentTaskService agentTaskService,
                                      StreamBuffers buffers,
                                      Runnable onEachChunk,
                                      Runnable onCancel,
                                      Runnable onPersist) {
        return source
                .doOnNext(chunk -> {
                    StreamChunkAccumulator.append(chunk, buffers.finalAnswer(), buffers.thinking());
                    if (onEachChunk != null) {
                        onEachChunk.run();
                    }
                })
                .doOnCancel(() -> {
                    if (onCancel != null) {
                        onCancel.run();
                    }
                    agentTaskService.stopTask(conversationId);
                })
                .doFinally(signalType -> {
                    if (onPersist != null) {
                        onPersist.run();
                    }
                    agentTaskService.stopTask(conversationId);
                });
    }

    public static void logStreamBuffers(StreamBuffers buffers) {
        log.info("Final Answer: {}", buffers.finalAnswer());
        log.info("Thinking process: {}", buffers.thinking());
    }

    public static Flux<String> conversationBusyError() {
        return Flux.error(new IllegalStateException(
                "The conversation is currently in progress, Please try again later."));
    }

    public static Flux<String> conversationBusyError(Supplier<String> messageSupplier) {
        return Flux.error(new IllegalStateException(messageSupplier.get()));
    }
}
