package com.genchat.application.strategy;

import com.genchat.common.AgentStreamEvent;
import com.genchat.common.TemplateSelectionResult;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.dto.AiPptTemplate;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.List;

@Slf4j
@Component
public class TemplateStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.OUTLINE;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        sink.tryEmitNext(new AgentStreamEvent.Thinking("Template styling is being designed...\n").toJSON());

        var requirement = inst.getRequirement();
        var pptTemplates = context.getDependencies().pptTemplateService().listAll();
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
            context.getDependencies().pptInstService().updateInst(inst);
            sink.tryEmitNext(new AgentStreamEvent.Thinking("✅ Once the template is designed, start generating the outline...\n").toJSON());
            context.continueStateMachine(inst, sink, question, thinkingBuffer);
        }catch (Exception e) {
            log.error("Template selection prompt failed", e);
            inst.setStatus(PptInstStatus.TEMPLATE.getCode());
            inst.setErrorMsg(e.getMessage());
            context.getDependencies().pptInstService().updateInst(inst);

            context.getDependencies().strategyFactory().executeFailedStrategy(inst, sink, question, thinkingBuffer, context);
        }
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
