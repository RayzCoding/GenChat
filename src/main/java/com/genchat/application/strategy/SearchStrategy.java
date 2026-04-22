package com.genchat.application.strategy;

import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import reactor.core.publisher.Sinks;

public class SearchStrategy implements PptStateStrategy {

    @Override
    public void execute(AiPptInst inst, Sinks.Many<String> sink, String query, StringBuilder thinkingBuffer, PptStateStrategyContext context) {

    }

    @Override
    public PptInstStatus getTargetStatus() {
        return null;
    }
}
