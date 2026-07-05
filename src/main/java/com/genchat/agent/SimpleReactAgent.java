package com.genchat.agent;

import com.genchat.agent.core.ReactChunkProcessor;
import com.genchat.agent.core.ReactToolSupport;
import com.genchat.common.prompts.PlanExecutePrompts;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.context.ContextCompactor;
import com.genchat.dto.SimpleReactResult;
import com.genchat.agent.model.AgentState;
import com.genchat.agent.model.RoundMode;
import com.genchat.agent.model.RoundState;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
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
    private String systemPrompt;
    private int maxRounds;
    protected Set<String> usedTools;
    private ContextCompactor contextCompactor;

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
        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer, question);

        return sink.asFlux()
                .doOnNext(thinkingBuffer::append)
                .doOnCancel(() -> hasSentFinalResult.set(true))
                .doFinally(signalType -> log.info("Final Answer: {}", finalAnswerBuffer));
    }

    private void scheduleRound(List<Message> messages,
                               Sinks.Many<String> sink,
                               AtomicInteger roundCounter,
                               AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer,
                               String currentQuestion) {
        roundCounter.incrementAndGet();
        log.info("SimpleReact round {}, message size: {}", roundCounter.get(), messages.size());
        compactMessages(messages, currentQuestion);

        var roundState = new RoundState();

        chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> ReactChunkProcessor.processChunk(chunk, sink, roundState, false))
                .doOnComplete(() -> finishRound(messages, sink,
                        roundState, roundCounter, hasSentFinalResult, finalAnswerBuffer, currentQuestion))
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
                             StringBuilder finalAnswerBuffer,
                             String currentQuestion) {
        // non tool
        if (roundState.mode != RoundMode.TOOL_CALL) {
            sink.tryEmitComplete();
            hasSentFinalResult.set(true);
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
                        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
                            forceFinalStream(messages, sink, hasSentFinalResult, currentQuestion);
                            return;
                        }
                        scheduleRound(messages, sink, roundCounter,
                                hasSentFinalResult, finalAnswerBuffer, currentQuestion);
                    }
                });
    }

    private void compactMessages(List<Message> messages, String currentQuestion) {
        if (contextCompactor != null) {
            contextCompactor.compact(messages, currentQuestion);
            log.debug("SimpleReact compacted, message count: {}", messages.size());
        }
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
                    ReactToolSupport.completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }
                var toolName = toolCall.name();
                var argsJson = toolCall.arguments();

                var toolCallback = ReactToolSupport.findTool(tools, toolName);
                if (Objects.isNull(toolCallback)) {
                    ReactToolSupport.addErrorToolResponse(messages, toolCall, "Not Found Tool:" + toolName);
                    ReactToolSupport.completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }
                try {
                    var result = toolCallback.call(argsJson);

                    var tr = new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolName, result.toString());
                    responseMap.put(toolCall.id(), tr);
                } catch (Exception ex) {
                    responseMap.put(toolCall.id(), new ToolResponseMessage.ToolResponse(toolCall.id(), toolName,
                            ReactToolSupport.toolErrorPayload("Tool execution failed: " + ex.getMessage())));
                } finally {
                    ReactToolSupport.completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                }
            });
        }
    }

    private void forceFinalStream(List<Message> messages,
                                  Sinks.Many<String> sink,
                                  AtomicBoolean hasSentFinalResult,
                                  String currentQuestion) {
        messages.add(new UserMessage("""
                You have reached the maximum number of reasoning rounds.
                Based on the current context information,
                please provide the final answer directly.
                Do not use any further tools.
                If the information is incomplete, please summarize and explain it reasonably.
                """));

        compactMessages(messages, currentQuestion);

        var finalTextBuffer = new StringBuilder();
        chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> ReactChunkProcessor.processForceFinalChunk(
                        chunk, sink, hasSentFinalResult, finalTextBuffer, false))
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

    public SimpleReactResult executeInternal(String conversationId, String question, boolean withReference) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());

        AgentState agentState = withReference ? new AgentState() : null;

        // Merge into a single SystemMessage (some models do not support multiple system messages)
        messages.add(new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n"
                + ReactAgentPrompts.getReactAgentSystemPrompt() + "\n\n" + systemPrompt));


        messages.add(new UserMessage("<question>" + question + "</question>"));

        // Iteration rounds
        int round = 0;

        while (true) {
            round++;
            if (maxRounds > 0 && round > maxRounds) {
                log.warn("=== Reached maxRounds ({}), forcing final answer ===", maxRounds);
                messages.add(new UserMessage("""
                        You have reached the maximum reasoning round limit.
                        Based on the context available so far,
                        provide the final answer directly.
                        Do not call any more tools.
                        If information is incomplete, summarize reasonably and explain the gaps.
                        """));

                compactMessages(messages, question);
                String forcedAnswer = chatClient.prompt().messages(messages).call().content();
                return SimpleReactResult.builder()
                        .answer(forcedAnswer)
                        .searchResults(agentState != null ? agentState.searchResults : Collections.emptyList())
                        .build();
            }

            compactMessages(messages, question);

            ChatClientResponse chatResponse = chatClient
                    .prompt()
                    .messages(messages)
                    .call()
                    .chatClientResponse();

            AssistantMessage.Builder builder = AssistantMessage.builder().content(chatResponse.chatResponse().getResult().getOutput().getText());

            // ===== No tool calls: treat as final answer =====
            if (!chatResponse.chatResponse().hasToolCalls()) {
                String finalText = chatResponse.chatResponse().getResult().getOutput().getText();
                return SimpleReactResult.builder()
                        .answer(finalText)
                        .searchResults(agentState.searchResults)
                        .build();
            }

            // ===== Tool calls present: execute tools =====
            List<AssistantMessage.ToolCall> toolCalls = chatResponse.chatResponse().getResult().getOutput().getToolCalls();
            messages.add(builder.toolCalls(toolCalls).build());

            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                String toolName = toolCall.name();
                String argsJson = toolCall.arguments();

                ToolCallback callback = ReactToolSupport.findTool(tools, toolName);
                if (callback == null) {
                    ReactToolSupport.addErrorToolResponse(messages, toolCall, "Tool not found: " + toolName);
                    continue;
                }

                try {
                    Object result = callback.call(argsJson);
                    String resultStr = Objects.toString(result, "");

                    if (agentState != null) {
                        ReactToolSupport.parseTavilySearchResult(resultStr, agentState);
                    }

                    ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolName, resultStr);
                    messages.add(ToolResponseMessage.builder()
                            .responses(List.of(tr))
                            .build());
                } catch (Exception ex) {
                    ReactToolSupport.addErrorToolResponse(messages, toolCall, "Tool execution failed: " + ex.getMessage());
                }
            }
        }
    }
}
