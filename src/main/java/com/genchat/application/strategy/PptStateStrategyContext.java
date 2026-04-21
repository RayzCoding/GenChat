package com.genchat.application.strategy;

import com.genchat.service.AiPptInstService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class PptStateStrategyContext {
    private AiPptInstService pptInstService;
    private String modifyQuestion;
    private boolean modifyMode;

    public PptStateStrategyContext(AiPptInstService pptInstService) {
        this.pptInstService = pptInstService;
    }
}
