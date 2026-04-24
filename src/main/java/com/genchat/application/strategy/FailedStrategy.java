package com.genchat.application.strategy;

import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.Objects;

@Slf4j
public class FailedStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.FAILED;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        log.info("Execute the failed state policy");
        var errorMsg = inst.getErrorMsg();
        String prompt;
        if (StringUtils.hasText(thinkingBuffer)) {
            var newQuestionPrompt = """
                    # Problems encountered in the previous round:
                    %s
                    
                    # Problems encountered in this round
                    %s
                    """.formatted(errorMsg, thinkingBuffer);
            prompt = PptBuilderPrompts.getFailurePrompt(newQuestionPrompt);
        } else {
            prompt = PptBuilderPrompts.getFailurePrompt("Unknown errors encountered during PPT generation");
        }
        var responseBuffer = new StringBuilder();
        var disposable = context.getChatClient().prompt()
                .messages(new UserMessage(prompt))
                .stream()
                .content()
                .doOnNext(chunk -> {
                    responseBuffer.append(chunk);
                    sink.tryEmitNext(AgentResponse.text(chunk));
                })
                .doOnComplete(() -> {
                    log.info("Failure indicates that the output is complete:{}", responseBuffer);
                    saveSessionResult(context, responseBuffer.toString(), thinkingBuffer);
                    sink.tryEmitComplete();
                })
                .doOnError(err -> {
                    log.error("Output failure indicates an exception", err);
                    var fallbackMsg = StringUtils.hasText(errorMsg) ? errorMsg : "PPT generation failed, please try again";
                    sink.tryEmitNext(AgentResponse.text(fallbackMsg));
                    saveSessionResult(context, fallbackMsg, thinkingBuffer);
                    sink.tryEmitComplete();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        context.setDisposable(inst.getConversationId(), disposable);
    }

    private void saveSessionResult(PptStateStrategyContext context,
                                   String response,
                                   StringBuilder thinkingBuffer) {
        var currentSessionId = context.getCurrentSessionId();
        var sessionService = context.getSessionService();
        if (currentSessionId == null) {
            return;
        }
        var chatSessionOptional = sessionService.queryById(currentSessionId);
        if (Objects.isNull(chatSessionOptional)) {
            return;
        }
        var aiChatSession = chatSessionOptional.get();
        aiChatSession.setAnswer(response);
        aiChatSession.setThinking(thinkingBuffer.toString());
        sessionService.updateSession(aiChatSession);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
