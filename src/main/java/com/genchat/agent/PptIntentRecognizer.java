package com.genchat.agent;

import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.PptIntent;
import com.genchat.dto.PptIntentResult;
import com.genchat.entity.PptInstStatus;
import com.genchat.service.AiPptInstService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Slf4j
public class PptIntentRecognizer {
    private final ChatClient chatClient;
    private final AiPptInstService pptInstService;

    public PptIntentRecognizer(ChatClient chatClient, AiPptInstService pptInstService) {
        this.chatClient = chatClient;
        this.pptInstService = pptInstService;
    }

    public PptIntentResult recognize(String conversationId, String question) {
        var latestInst = pptInstService.getLatestInst(conversationId);
        if (ObjectUtils.isEmpty(latestInst)) {
            log.info("Conversation non contain ppt instant, defualt create new instant.");
            return new PptIntentResult(PptIntent.CREATE_PPT,
                    "Conversation non contain ppt instant, defualt create new instant.");
        }
        var pptInstStatus = PptInstStatus.fromCode(latestInst.getStatus());
        var errorMsg = latestInst.getErrorMsg();

        // Check if breakpoint reconnection is required
        if (needsResume(pptInstStatus, errorMsg, question)) {
            log.info("Conversation resume needs resume. status: {}, error msg: {}", pptInstStatus, errorMsg);
            return new PptIntentResult(PptIntent.RESUME_PPT,
                    "The last execution was detected as incomplete, Continue from status " + pptInstStatus);
        }
        // If it's in the SUCCESS state, call the LLM for intent recognition (CREATE_PPT or MODIFY_PPT)
        if (pptInstStatus == PptInstStatus.SUCCESS) {
            return recognizeWithLLM(question);
        }
        // For other intermediate states (non-failed), it also defaults to CREATE_PPT (new)
        return new PptIntentResult(PptIntent.CREATE_PPT, "Default Create, status: " + pptInstStatus);
    }

    private PptIntentResult recognizeWithLLM(String question) {
        var recognitionPrompt = PptBuilderPrompts.INTENT_RECOGNITION_PROMPT;
        var converter = new BeanOutputConverter<PptIntentResult>(new ParameterizedTypeReference<>() {});
        try {
            var content = chatClient.prompt()
                    .messages(new SystemMessage(recognitionPrompt),
                            new UserMessage("<question>" + question + "</question>"))
                    .call()
                    .content();
            log.info("Conversation recognition prompt. content: {}", content);
            return converter.convert(content);
        }catch (Exception e) {
            return new PptIntentResult(PptIntent.CREATE_PPT, "If the intent recognition fails, it will be created by default");
        }
    }

    private boolean needsResume(PptInstStatus pptInstStatus, String errorMsg, String question) {
        if (StringUtils.hasLength(errorMsg)) {
            return true;
        }
        String[] resumeKeywords = {"继续", "重试", "resume", "retry", "继续执行", "继续生成"};
        var containResume = Arrays.stream(resumeKeywords)
                .anyMatch(keyword -> question.toLowerCase().contains(keyword));
        if (containResume) {
            return true;
        }
        if (pptInstStatus != PptInstStatus.SUCCESS && pptInstStatus != PptInstStatus.INIT) {
            String[] newKeywords = {"新建", "重新", "重新生成", "new", "create new"};
            var containNew = Arrays.stream(newKeywords)
                    .anyMatch(keyword -> question.toLowerCase().contains(keyword));
            if (!containNew) {
                return true;
            }
        }
        return false;
    }
}
