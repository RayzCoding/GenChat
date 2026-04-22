package com.genchat.application.strategy;

import com.genchat.common.AgentResponse;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PptStateStrategyFactory {

    private static final Map<PptInstStatus, PptStateStrategy> STRATEGY_MAP = new HashMap<>();

    private PptStateStrategyFactory() {
        STRATEGY_MAP.put(PptInstStatus.INIT, new RequirementStrategy());
        STRATEGY_MAP.put(PptInstStatus.REQUIREMENT, new RequirementStrategy());
        STRATEGY_MAP.put(PptInstStatus.TEMPLATE, new TemplateStrategy());
        STRATEGY_MAP.put(PptInstStatus.OUTLINE, new OutlineStrategy());
        STRATEGY_MAP.put(PptInstStatus.SEARCH, new SearchStrategy());
        STRATEGY_MAP.put(PptInstStatus.SCHEMA, new SchemaStrategy());
        STRATEGY_MAP.put(PptInstStatus.RENDER, new RenderStrategy());
        STRATEGY_MAP.put(PptInstStatus.SUCCESS, new SuccessStrategy());
        STRATEGY_MAP.put(PptInstStatus.FAILED, new FailedStrategy());
    }

    public static PptStateStrategyFactory getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public void executeNextState(AiPptInst pptInst,
                                 Sinks.Many<String> sink,
                                 String question,
                                 StringBuilder thinkingBuffer,
                                 PptStateStrategyContext strategyContext) {
        try {
            var pptInstService = strategyContext.getPptInstService();
            var latestInst = pptInstService.getInstById(pptInst.getId());
            if (latestInst.isPresent()) {
                pptInst = latestInst.get();
            }
            var pptInstStatus = PptInstStatus.fromCode(pptInst.getStatus());
            log.info("Intent status: {} ", pptInstStatus);
            if (StringUtils.hasLength(pptInst.getErrorMsg()) &&
                    pptInstStatus != PptInstStatus.SUCCESS) {
                log.info("Breakpoint reconnection detected, status is {}", pptInst.getStatus());
                pptInst.setErrorMsg("");
                pptInstService.updateInst(pptInst);
            }
            var strategy = getStrategy(pptInstStatus);
            strategy.execute(pptInst, sink, question, thinkingBuffer, strategyContext);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            sink.tryEmitError(e);
        }
    }

    public PptStateStrategy getStrategy(PptInstStatus status) {
        var strategy = STRATEGY_MAP.get(status);
        if (strategy == null) {
            log.warn("No policy for the status was found: {}", status);
            return new DefaultStrategy();
        }
        return strategy;
    }

    public void executeSchemaStrategy(AiPptInst latestInst,
                                      Sinks.Many<String> sink,
                                      String question,
                                      StringBuilder thinkingBuffer,
                                      PptStateStrategyContext strategyContext) {
        var schemaStrategy = new SchemaStrategy();
        var schemaModifyPrompt = PptBuilderPrompts.getSchemaModifyPrompt(question, latestInst.getPptSchema());
        schemaStrategy.executeWithModifyPrompt(latestInst, sink, question, thinkingBuffer, strategyContext, schemaModifyPrompt);
    }

    public void executeFailedStrategy(AiPptInst inst,
                                      Sinks.Many<String> sink,
                                      String question,
                                      StringBuilder thinkingBuffer,
                                      PptStateStrategyContext context) {
        getStrategy(PptInstStatus.FAILED).execute(inst, sink, question, thinkingBuffer, context);
    }

    private static class SingletonHolder {
        private static final PptStateStrategyFactory INSTANCE = new PptStateStrategyFactory();
    }

    private static class DefaultStrategy implements PptStateStrategy {
        @Override
        public void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                            StringBuilder thinkingBuffer, PptStateStrategyContext context) {
            log.warn("Unknown state: {}", inst.getStatus());
            sink.tryEmitNext(AgentResponse.thinking("❌ abnormal status, terminate execution\n"));
            sink.tryEmitComplete();
        }

        @Override
        public PptInstStatus getTargetStatus() {
            return PptInstStatus.FAILED;
        }
    }
}
