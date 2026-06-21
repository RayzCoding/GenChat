package com.genchat.application.strategy;

import com.genchat.agent.SimpleReactAgent;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import reactor.core.publisher.Sinks;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;

@Slf4j
@Component
public class SearchStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.TEMPLATE;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        // Emit thinking status
        sink.tryEmitNext(new AgentStreamEvent.Thinking("🔍Information is being collected...\n").toJSON());
        // Build search prompt
        var searchInfoPrompt = PptBuilderPrompts.getSearchInfoPrompt(inst.getRequirement());
        // Inject tavily tool
        var messages = Collections.synchronizedList(new ArrayList<Message>());
        var reactAgentSystemPrompt = ReactAgentPrompts.getReactAgentSystemPrompt();
        messages.add(new SystemMessage(reactAgentSystemPrompt));

        var searchResultBuffer = new StringBuilder();
        // Execute strategy
        var simpleReactAgent = new SimpleReactAgent(context.getChatModel(), context.getTools());
        var disposable = simpleReactAgent.stream(searchInfoPrompt)
                .doOnNext(chunk -> {
                    searchResultBuffer.append(chunk);
                    sink.tryEmitNext(new AgentStreamEvent.Thinking(chunk).toJSON());
                })
                .doOnComplete(() -> {
                    var searchResult = searchResultBuffer.toString();
                    log.info("Search result: {}", searchResult);
                    inst.setStatus(TARGET_STATUS.getCode());
                    inst.setSearchInfo(searchResult);
                    context.getPptInstService().updateInst(inst);
                    sink.tryEmitNext(new AgentStreamEvent.Thinking("\n✅Once the relevant information is gathered, start selecting the template\n").toJSON());
                    context.continueStateMachine(inst, sink, question, thinkingBuffer);
                })
                .doOnError(throwable -> {
                    log.error("Search result: {}", searchResultBuffer, throwable);
                    inst.setErrorMsg(throwable.getMessage());
                    inst.setStatus(PptInstStatus.SEARCH.getCode());
                    context.getPptInstService().updateInst(inst);
                    context.getStrategyFactory().executeFailedStrategy(inst, sink, question, thinkingBuffer, context);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        context.setDisposable(inst.getConversationId(), disposable);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
