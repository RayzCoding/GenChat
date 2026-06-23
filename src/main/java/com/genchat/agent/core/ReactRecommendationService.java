package com.genchat.agent.core;

import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.common.utils.JacksonJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class ReactRecommendationService {

    private ReactRecommendationService() {
    }

    public static String generate(ChatModel chatModel,
                                  List<Message> historyMessages,
                                  String currentQuestion,
                                  String currentAnswer) {
        try {
            var messages = new ArrayList<Message>();
            messages.add(new SystemMessage(ReactAgentPrompts.getRecommendPrompt()));
            if (!CollectionUtils.isEmpty(historyMessages)) {
                messages.addAll(historyMessages);
            }
            messages.add(new UserMessage("Current conversation: "));
            messages.add(new UserMessage(currentQuestion));
            if (StringUtils.hasLength(currentAnswer)) {
                messages.add(new AssistantMessage(currentAnswer));
            }

            var converter = new BeanOutputConverter<List<String>>(new ParameterizedTypeReference<>() {
            });
            messages.add(new UserMessage("Please generate 3 recommended questions based on the above dialogue. The output format is as follows：\n"
                    + converter.getFormat()));
            var response = ChatClient.builder(chatModel).build()
                    .prompt()
                    .messages(messages)
                    .call()
                    .content();
            if (response != null && !response.isEmpty()) {
                List<String> recommendations = converter.convert(response);
                if (!CollectionUtils.isEmpty(recommendations)) {
                    var jsonStr = JacksonJson.toJson(recommendations);
                    log.info("The recommendation question has been successfully generated: {}", jsonStr);
                    return jsonStr;
                }
            }
            log.warn("Failed to generate recommendation questions, response format is invalid: {}", response);
            return null;
        } catch (Exception e) {
            log.error("Anomaly in generating recommendation questions", e);
            return null;
        }
    }
}
