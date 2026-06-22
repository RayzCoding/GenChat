package com.genchat.common.prompts;

/**
 * PPT Builder prompts loaded from classpath templates.
 */
public class PptBuilderPrompts {

    public static final String INTENT_RECOGNITION_PROMPT = PromptLoader.load("ppt/intent-recognition.md");
    public static final String REQUIREMENT_PROMPT = PromptLoader.load("ppt/requirement.md");

    public static String getOutlinePrompt(String requirement, String templateSchema, String templateName, String searchInfo) {
        return PromptLoader.format("ppt/outline.md", requirement, searchInfo, templateName, templateSchema);
    }

    public static String getSearchInfoPrompt(String requirement) {
        return PromptLoader.format("ppt/search-info.md", requirement);
    }

    public static String getTemplateSelectionPrompt(String requirement, String templatesInfo) {
        return PromptLoader.format("ppt/template-selection.md", requirement, templatesInfo);
    }

    public static String getSchemaGenerationPrompt(String templateSchema, String outline) {
        return PromptLoader.format("ppt/schema-generation.md", templateSchema, outline);
    }

    public static String getSchemaModifyPrompt(String userRequest, String currentSchema) {
        return PromptLoader.format("ppt/schema-modify.md", userRequest, currentSchema);
    }

    public static String getSummaryPrompt(String requirement, String fileUrl, int pageCount) {
        return PromptLoader.format("ppt/summary.md", requirement, pageCount, fileUrl, pageCount, fileUrl);
    }

    public static String getModifySummaryPrompt(String modifyRequest, String fileUrl) {
        return PromptLoader.format("ppt/modify-summary.md", modifyRequest, fileUrl, fileUrl);
    }

    public static String getFailurePrompt(String thinkingProcess) {
        return PromptLoader.format("ppt/failure.md", thinkingProcess);
    }

    private PptBuilderPrompts() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
