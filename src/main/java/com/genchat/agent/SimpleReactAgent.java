package com.genchat.agent;

import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.entity.RoundMode;
import com.genchat.entity.RoundState;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * It does not contain any conversational memory and only answers the current question
 */
@Slf4j
@Setter
public class SimpleReactAgent {
    private final ChatModel chatModel;
    private final List<ToolCallback> tools;
    private ChatClient chatClient;
    private final String systemPrompt;
    private int maxRounds;
    protected Set<String> usedTools;

    public SimpleReactAgent(ChatModel chatModel,
                            List<ToolCallback> webSearchToolCallbacks) {
        this.systemPrompt = "";
        this.tools = webSearchToolCallbacks;
        this.chatModel = chatModel;
        this.usedTools = new HashSet<>();
        initChatClient();
    }

    private void initChatClient() {
        var toolCallingChatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .internalToolExecutionEnabled(false)
                .build();
        this.chatClient = ChatClient.builder(this.chatModel)
                .defaultOptions(toolCallingChatOptions)
                .defaultToolCallbacks(tools)
                .build();
    }

    public Flux<String> stream(String question) {
        // loading system prompt
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        var reactAgentSystemPrompt = ReactAgentPrompts.getReactAgentSystemPrompt();
        messages.add(new SystemMessage(reactAgentSystemPrompt));
        if (StringUtils.hasLength(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage("<question>" + question + "</question>"));
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        // iteration round
        var roundCounter = new AtomicInteger(0);
        // Whether to send the final result flag
        var hasSentFinalResult = new AtomicBoolean(false);
        // Collect the final answer (in plain text) and store it in memory
        var finalAnswerBuffer = new StringBuilder();
        // Collecting the thought process
        var thinkingBuffer = new StringBuilder();

        // add Round
        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer);

        return sink.asFlux()
                .doOnNext(thinkingBuffer::append)
                .doOnCancel(() -> hasSentFinalResult.set(true))
                .doFinally(signalType -> log.info("Final Answer: {}", finalAnswerBuffer));
    }

    private void scheduleRound(List<Message> messages,
                               Sinks.Many<String> sink,
                               AtomicInteger roundCounter,
                               AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer) {
        roundCounter.incrementAndGet();
        var roundState = new RoundState();

        chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, roundState))
                .doOnComplete(() -> finishRound(messages, sink,
                        roundState, roundCounter, hasSentFinalResult, finalAnswerBuffer))
                .doOnError(error -> {
                    if (!hasSentFinalResult.get()) {
                        hasSentFinalResult.set(true);
                        sink.tryEmitError(error);
                    }
                })
                .subscribe();
    }

    private void finishRound(List<Message> messages,
                             Sinks.Many<String> sink,
                             RoundState roundState,
                             AtomicInteger roundCounter,
                             AtomicBoolean hasSentFinalResult,
                             StringBuilder finalAnswerBuffer) {
        // non tool
        if (roundState.mode != RoundMode.TOOL_CALL) {
            sink.tryEmitComplete();
            hasSentFinalResult.set(true);
            return;
        }
        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            forceFinalStream(messages, sink, hasSentFinalResult);
            return;
        }
        // tool call
        var assistantMessage = AssistantMessage.builder()
                .toolCalls(roundState.toolCalls)
                .build();
        messages.add(assistantMessage);

        executeToolCalls(roundState.toolCalls,
                messages, hasSentFinalResult,
                () -> {
                    if (!hasSentFinalResult.get()) {
                        scheduleRound(messages, sink, roundCounter,
                                hasSentFinalResult, finalAnswerBuffer);
                    }
                });
    }

    private void executeToolCalls(List<AssistantMessage.ToolCall> toolCalls,
                                  List<Message> messages,
                                  AtomicBoolean hasSentFinalResult,
                                  Runnable onComplete) {
        var completedCount = new AtomicInteger(0);
        var totalToolCalls = toolCalls.size();
        var responseMap = new ConcurrentHashMap<String, ToolResponseMessage.ToolResponse>();

        for (var toolCall : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                if (hasSentFinalResult.get()) {
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                }
                var toolName = toolCall.name();
                var argsJson = toolCall.arguments();

                var toolCallback = findTool(toolName);
                if (Objects.isNull(toolCallback)) {
                    addErrorToolResponse(messages, toolCall, "Not Found Tool:" + toolName);
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }
                try {
                    var result = toolCallback.call(argsJson);

                    var tr = new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolName, result.toString());
                    responseMap.put(toolCall.id(), tr);
                } catch (Exception ex) {
                    responseMap.put(toolCall.id(), new ToolResponseMessage.ToolResponse(toolCall.id(), toolName,
                            "{ \"error\": \"Tool execution failed：" + ex.getMessage() + "\" }"));
                } finally {
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                }
            });
        }
    }

    private void addErrorToolResponse(List<Message> messages, AssistantMessage.ToolCall toolCall, String errMsg) {
        ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                toolCall.id(),
                toolCall.name(),
                "{ \"error\": \"" + errMsg + "\" }"
        );

        messages.add(ToolResponseMessage.builder()
                .responses(List.of(tr))
                .build());
    }

    private ToolCallback findTool(String name) {
        return tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private void completeToolCall(AtomicInteger completedCount, int total,
                                  Map<String, ToolResponseMessage.ToolResponse> responseMap,
                                  List<AssistantMessage.ToolCall> originalToolCalls,
                                  List<Message> messages,
                                  Runnable onComplete) {
        int current = completedCount.incrementAndGet();
        if (current >= total) {
            // Reorganize the results in the order of the original toolCalls
            var sortedResponses = new ArrayList<ToolResponseMessage.ToolResponse>();
            for (AssistantMessage.ToolCall tc : originalToolCalls) {
                ToolResponseMessage.ToolResponse response = responseMap.get(tc.id());
                if (response != null) {
                    sortedResponses.add(response);
                } else {
                    // If a tool call is unresponsive, add an error response
                    sortedResponses.add(new ToolResponseMessage.ToolResponse(
                            tc.id(), tc.name(), "{ \"error\": \"Tool response is missing\" }"));
                }
            }
            // Add all tool responses at once (in original order)
            messages.add(ToolResponseMessage.builder()
                    .responses(sortedResponses)
                    .build());
            onComplete.run();
        }
    }

    private void forceFinalStream(List<Message> messages,
                                  Sinks.Many<String> sink,
                                  AtomicBoolean hasSentFinalResult) {
        messages.add(new UserMessage("""
                You have reached the maximum number of reasoning rounds.
                Based on the current context information,
                please provide the final answer directly.
                Do not use any further tools.
                If the information is incomplete, please summarize and explain it reasonably.
                """));

        var finalTextBuffer = new StringBuilder();
        chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                        return;
                    }
                    var text = chunk.getResult()
                            .getOutput()
                            .getText();
                    if (StringUtils.hasLength(text) && !hasSentFinalResult.get()) {
                        sink.tryEmitNext(text);
                        finalTextBuffer.append(text);
                    }
                })
                .doOnComplete(() -> {
                    hasSentFinalResult.set(true);
                    sink.tryEmitComplete();
                })
                .doOnError(error -> {
                    hasSentFinalResult.set(true);
                    sink.tryEmitError(error);
                })
                .subscribe();
    }

    private void processChunk(ChatResponse chunk, Sinks.Many<String> sink, RoundState roundState) {
        if (Objects.isNull(chunk)) {
            return;
        }
        var gen = chunk.getResult();
        var text = gen.getOutput().getText();
        var toolCalls = gen.getOutput().getToolCalls();
        // into Tool call model
        if (!ObjectUtils.isEmpty(toolCalls)) {
            roundState.mode = RoundMode.TOOL_CALL;
            toolCalls.forEach(toolCall -> mergeToolCall(roundState, toolCall));
            return;
        }
        // cache text
        if (StringUtils.hasLength(text)) {
            sink.tryEmitNext(text);
            roundState.textBuffer.append(text);
        }
    }

    private void mergeToolCall(RoundState state, AssistantMessage.ToolCall incoming) {
        for (int i = 0; i < state.toolCalls.size(); i++) {
            AssistantMessage.ToolCall existing = state.toolCalls.get(i);

            if (existing.id().equals(incoming.id())) {
                String mergedArgs = Objects.toString(existing.arguments(), "") + Objects.toString(incoming.arguments(), "");

                state.toolCalls.set(i,
                        new AssistantMessage.ToolCall(existing.id(), "function", existing.name(), mergedArgs)
                );
                return;
            }
        }

        // New tool call
        state.toolCalls.add(incoming);
    }
}
