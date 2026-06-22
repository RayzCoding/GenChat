package com.genchat.common.prompts;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Plan-Execute Agent prompts loaded from classpath templates.
 */
public final class PlanExecutePrompts {

    private PlanExecutePrompts() {
    }

    public static String getCurrentTime() {
        return "当前正确的系统时间：" + LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static final String PLAN = PromptLoader.load("plan-execute/plan.md");
    public static final String EXECUTE = PromptLoader.load("plan-execute/execute.md");
    public static final String CRITIQUE = PromptLoader.load("plan-execute/critique.md");
    public static final String COMPRESS = PromptLoader.load("plan-execute/compress.md");
    public static final String SUMMARIZE = PromptLoader.load("plan-execute/summarize.md");
    public static final String REQUIREMENT_CLARIFICATION = PromptLoader.load("plan-execute/requirement-clarification.md");
    public static final String RESEARCH_TOPIC_GENERATION = PromptLoader.load("plan-execute/research-topic-generation.md");
}
