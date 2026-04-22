package com.genchat.application.strategy;

import com.genchat.common.AgentResponse;
import com.genchat.common.TemplateSelectionResult;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.dto.AiPptTemplate;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Sinks;

import java.util.List;

@Slf4j
public class TemplateStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.OUTLINE;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        sink.tryEmitNext(AgentResponse.thinking("Template styling is being designed...\n"));

        var requirement = inst.getRequirement();
        var pptTemplates = context.getPptTemplateService().listAll();
        var templatesInfo = new StringBuilder();
        pptTemplates.forEach(template -> {
            templatesInfo.append(String.format("""
                            --------------------------------
                            template_code: %s
                            template name: %s
                            Applicable styles: %s
                            Number of template pages: %d
                            Template description: %s
                            """,
                    template.getTemplateCode(),
                    template.getTemplateName(),
                    template.getStyleTags(),
                    template.getSlideCount(),
                    template.getTemplateDesc()
            ));
        });
        var templateSelectionPrompt = PptBuilderPrompts.getTemplateSelectionPrompt(requirement, templatesInfo.toString());

        try {
            var result = context.getChatClient()
                    .prompt(templateSelectionPrompt)
                    .call()
                    .entity(TemplateSelectionResult.class);
            log.info("Template selection result: {}", result);
            inst.setTemplateCode(result.getTemplateCode());
            inst.setStatus(TARGET_STATUS.getCode());
            context.getPptInstService().updateInst(inst);
            sink.tryEmitNext(AgentResponse.thinking("✅ Once the template is designed, start generating the outline...\n"));
            context.continueStateMachine(inst, sink, question, thinkingBuffer);
        }catch (Exception e) {
            log.error("Template selection prompt failed", e);
            inst.setStatus(PptInstStatus.TEMPLATE.getCode());
            inst.setErrorMsg(e.getMessage());
            context.getPptInstService().updateInst(inst);

            PptStateStrategyFactory.getInstance().executeFailedStrategy(inst, sink, question, thinkingBuffer, context);
        }
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
