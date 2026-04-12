package com.genchat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MultimodalChatModelConfig {
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;
    @Value("${spring.ai.openai.multimodal.options.model}")
    private String model;
    @Value("${spring.ai.openai.multimodal.options.temperature}")
    private Double temperature;


    @Bean
    public OpenAiChatModel multimodalChatModel() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature(temperature)
                .model(model)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(baseUrl)
                        .apiKey(new SimpleApiKey(apiKey))
                        .build())
                .defaultOptions(options)
                .build();
        log.info("Multimodal chat model initialized successfully.");
        return model;
    }
}
