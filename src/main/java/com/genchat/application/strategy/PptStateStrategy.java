package com.genchat.application.strategy;

import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import reactor.core.publisher.Sinks;

/**
 * PPT state strategy interface
 * Uses strategy pattern to handle processing logic for different states
 */
public interface PptStateStrategy {

    /**
     * Execute the processing logic for this state
     *
     * @param inst            PPT instance
     * @param sink            response stream
     * @param query           user query
     * @param thinkingBuffer  thinking buffer
     * @param context         strategy context
     */
    void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                 StringBuilder thinkingBuffer, PptStateStrategyContext context);

    /**
     * Get the target status corresponding to this strategy
     * After successful execution, the status should transition to this status
     *
     * @return target status
     */
    PptInstStatus getTargetStatus();
}
