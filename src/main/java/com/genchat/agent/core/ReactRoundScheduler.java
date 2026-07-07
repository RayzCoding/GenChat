package com.genchat.agent.core;

import com.genchat.common.AgentStreamEvent;
import com.genchat.common.ToolRecord;
import com.genchat.agent.model.AgentState;
import com.genchat.agent.model.RoundMode;
import com.genchat.agent.model.RoundState;
import com.genchat.context.ContextCompactor;
import com.genchat.service.AgentTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class ReactRoundScheduler {

    public interface Callbacks {
        void emitToolThinking(String toolName, String argsJson, Sinks.Many<String> sink);

        void handleToolResult(String toolName, String result, AgentState agentState);

        void onNonToolFinish(String finalText,
                             Sinks.Many<String> sink,
                             String conversationId,
                             AgentState agentState);

        void onForceFinalComplete(String finalText,
                                  Sinks.Many<String> sink,
                                  String conversationId,
                                  AgentState agentState);

        String getForceFinalSystemPrompt();

        String getSystemPrompt();
    }

    private final ChatClient chatClient;
    private final List<ToolCallback> tools;
    private final AgentTaskService agentTaskService;
    private final int maxRounds;
    private final int maxRetries;
    private final List<ToolRecord> toolRecords;
    private final Callbacks callbacks;
    private final ContextCompactor contextCompactor;
    private final String currentQuestion;

    public ReactRoundScheduler(ChatClient chatClient,
                               List<ToolCallback> tools,
                               AgentTaskService agentTaskService,
                               int maxRounds,
                               int maxRetries,
                               List<ToolRecord> toolRecords,
                               Callbacks callbacks,
                               ContextCompactor contextCompactor,
                               String currentQuestion) {
        this.chatClient = chatClient;
        this.tools = tools;
        this.agentTaskService = agentTaskService;
        this.maxRounds = maxRounds;
        this.maxRetries = maxRetries;
        this.toolRecords = toolRecords;
        this.callbacks = callbacks;
        this.contextCompactor = contextCompactor;
        this.currentQuestion = currentQuestion;
    }

    public void scheduleRound(List<Message> messages,
                              Sinks.Many<String> sink,
                              AtomicInteger roundCounter,
                              AtomicBoolean hasSentFinalResult,
                              StringBuilder finalAnswerBuffer,
                              String conversationId,
                              AgentState agentState,
                              StringBuilder thinkingBuffer) {
        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer,
                conversationId, agentState, thinkingBuffer, 0);
    }

    private void scheduleRound(List<Message> messages,
                               Sinks.Many<String> sink,
                               AtomicInteger roundCounter,
                               AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer,
                               String conversationId,
                               AgentState agentState,
                               StringBuilder thinkingBuffer,
                               int retryAttempt) {
        roundCounter.incrementAndGet();
        log.info("Round Counter: {}, message size:{}", roundCounter.get(), messages.size());
        if (contextCompactor != null) {
            contextCompactor.compact(messages, currentQuestion);
            log.info("=== Round {} compacted, message count: {} ===", roundCounter.get(), messages.size());
        }

        var roundState = new RoundState();

        var disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> ReactChunkProcessor.processChunk(chunk, sink, roundState, true))
                .doOnComplete(() -> finishRound(messages, sink,
                        roundState, roundCounter,
                        hasSentFinalResult, finalAnswerBuffer,
                        conversationId, agentState, thinkingBuffer))
                .onErrorResume(error -> {
                    if (shouldRetryStream(retryAttempt)) {
                        log.warn("LLM stream error (attempt {}/{}), retrying in {}ms: {}",
                                retryAttempt + 1, maxRetries, 1000, error.getMessage());
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED", "LLM call failed, and the attempt is being reattempted ("
                                + (retryAttempt + 1) + "/" + maxRetries + ")", error.getMessage()).toJSON());
                        Schedulers.boundedElastic().schedule(() -> scheduleRound(messages, sink, roundCounter,
                                hasSentFinalResult, finalAnswerBuffer,
                                conversationId, agentState, thinkingBuffer, retryAttempt + 1));
                    } else {
                        log.error("LLM stream error, retries exhausted {}ms: {}", maxRetries, error.getMessage());
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED",
                                "LLM call failed, and the attempt is being reattempted (" + maxRetries + ")",
                                error.getMessage()).toJSON());
                        sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
                        hasSentFinalResult.set(true);
                        sink.tryEmitComplete();
                    }
                    return Flux.empty();
                })
                .subscribe();

        if (conversationId != null) {
            agentTaskService.setDisposable(conversationId, disposable);
        }
    }

    private void finishRound(List<Message> messages,
                             Sinks.Many<String> sink,
                             RoundState roundState,
                             AtomicInteger roundCounter,
                             AtomicBoolean hasSentFinalResult,
                             StringBuilder finalAnswerBuffer,
                             String conversationId,
                             AgentState agentState,
                             StringBuilder thinkingBuffer) {
        if (roundState.mode != RoundMode.TOOL_CALL) {
            String finalText = roundState.textBuffer.toString();
            callbacks.onNonToolFinish(finalText, sink, conversationId, agentState);
            sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
            sink.tryEmitComplete();
            hasSentFinalResult.set(true);
            return;
        }

        var assistantMessage = AssistantMessage.builder()
                .toolCalls(roundState.toolCalls)
                .build();
        messages.add(assistantMessage);
        executeToolCalls(sink, roundState.toolCalls,
                messages, hasSentFinalResult,
                agentState,
                () -> {
                    if (!hasSentFinalResult.get()) {
                        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
                            forceFinalStream(messages, sink, hasSentFinalResult, roundState, conversationId, agentState);
                            return;
                        }
                        scheduleRound(messages, sink, roundCounter,
                                hasSentFinalResult, finalAnswerBuffer,
                                conversationId, agentState, thinkingBuffer);
                    }
                });
    }

    private void executeToolCalls(Sinks.Many<String> sink,
                                  List<AssistantMessage.ToolCall> toolCalls,
                                  List<Message> messages,
                                  AtomicBoolean hasSentFinalResult,
                                  AgentState agentState,
                                  Runnable onComplete) {
        var completedCount = new AtomicInteger(0);
        var totalToolCalls = toolCalls.size();
        Map<String, ToolResponseMessage.ToolResponse> responseMap = new ConcurrentHashMap<>();

        for (var toolCall : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                if (hasSentFinalResult.get()) {
                    ReactToolSupport.completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }
                var toolName = toolCall.name();
                var argsJson = toolCall.arguments();
                log.info(">>> ToolStart: {} | args: {}", toolName, argsJson);
                sink.tryEmitNext(new AgentStreamEvent.ToolStart(toolName, toolCall.id(), argsJson).toJSON());

                var toolCallback = ReactToolSupport.findTool(tools, toolName);
                if (Objects.isNull(toolCallback)) {
                    String errorMsg = "Tool not found：" + toolName;
                    log.warn("<<< ToolEnd (NOT_FOUND): {}", toolName);
                    sink.tryEmitNext(new AgentStreamEvent.ToolEnd(toolName, toolCall.id(), errorMsg).toJSON());
                    responseMap.put(toolCall.id(), new ToolResponseMessage.ToolResponse(
                            toolCall.id(),
                            toolCall.name(),
                            "{ \"error\": \"" + "Not Found Tool:" + toolName + "\" }"
                    ));
                    ReactToolSupport.completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }

                callbacks.emitToolThinking(toolName, argsJson, sink);

                try {
                    var result = toolCallback.call(argsJson);
                    toolRecords.add(new ToolRecord(toolName, toolCall.id(), argsJson, result));
                    log.info("<<< ToolEnd: {}| result: {}", toolName, result);
                    sink.tryEmitNext(new AgentStreamEvent.ToolEnd(toolName, toolCall.id(), result).toJSON());
                    responseMap.put(toolCall.id(), new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, result));
                    callbacks.handleToolResult(toolName, result, agentState);
                } catch (Exception ex) {
                    responseMap.put(toolCall.id(), new ToolResponseMessage.ToolResponse(
                            toolCall.id(),
                            toolCall.name(),
                            "{ \"error\": \"" + "Tool Execute failed:" + ex.getMessage() + "\" }"
                    ));
                } finally {
                    if (!hasSentFinalResult.get()) {
                        ReactToolSupport.completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    }
                }
            });
        }
    }

    private void forceFinalStream(List<Message> messages,
                                  Sinks.Many<String> sink,
                                  AtomicBoolean hasSentFinalResult,
                                  RoundState roundState,
                                  String conversationId,
                                  AgentState agentState) {
        forceFinalStream(messages, sink, hasSentFinalResult, roundState, conversationId, agentState, 0);
    }

    private void forceFinalStream(List<Message> messages,
                                  Sinks.Many<String> sink,
                                  AtomicBoolean hasSentFinalResult,
                                  RoundState roundState,
                                  String conversationId,
                                  AgentState agentState,
                                  int retryAttempt) {
        var newMessages = new ArrayList<Message>();
        newMessages.add(new SystemMessage(callbacks.getForceFinalSystemPrompt()));
        if (org.springframework.util.StringUtils.hasLength(callbacks.getSystemPrompt())) {
            newMessages.add(new SystemMessage(callbacks.getSystemPrompt()));
        }

        messages.forEach(message -> {
            if (!(message instanceof SystemMessage)) {
                newMessages.add(message);
            }
        });
        newMessages.add(new UserMessage("""
                You have reached the maximum number of reasoning rounds.
                Based on the current context information,
                please provide the final answer directly.
                Do not use any further tools.
                If the information is incomplete, please summarize and explain it reasonably.
                """));

        messages.clear();
        messages.addAll(newMessages);

        var finalTextBuffer = new StringBuilder();
        var disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> ReactChunkProcessor.processForceFinalChunk(
                        chunk, sink, hasSentFinalResult, finalTextBuffer, true))
                .doOnComplete(() -> {
                    var finalText = finalTextBuffer.toString();
                    callbacks.onForceFinalComplete(finalText, sink, conversationId, agentState);
                    sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
                    hasSentFinalResult.set(true);
                    sink.tryEmitComplete();
                })
                .onErrorResume(error -> {
                    if (shouldRetryStream(retryAttempt)) {
                        log.warn("forceFinal stream error (attempt {}/{}), retrying in {}ms: {}",
                                retryAttempt + 1, maxRetries, 1000, error.getMessage());
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED", "LLM call failed, and the attempt is being reattempted ("
                                + (retryAttempt + 1) + "/" + maxRetries + ")", error.getMessage()).toJSON());
                        Schedulers.boundedElastic().schedule(() -> forceFinalStream(messages, sink,
                                hasSentFinalResult, roundState, conversationId, agentState, retryAttempt + 1));
                    } else {
                        log.error("forceFinal stream error, retries exhausted {}ms: {}", maxRetries, error.getMessage());
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED",
                                "LLM call failed, and the attempt is being reattempted (" + maxRetries + ")",
                                error.getMessage()).toJSON());
                        sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
                        hasSentFinalResult.set(true);
                        sink.tryEmitComplete();
                    }
                    return reactor.core.publisher.Flux.empty();
                })
                .subscribe();

        if (conversationId != null) {
            agentTaskService.setDisposable(conversationId, disposable);
        }
    }

    boolean shouldRetryStream(int retryAttempt) {
        return retryAttempt < maxRetries;
    }
}
