package com.genchat.config;

import com.genchat.context.ContextPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ContextConfig {

    private final GenChatProperties genChatProperties;

    @Bean
    public ContextPolicy contextPolicy() {
        var context = genChatProperties.getContext();
        return ContextPolicy.builder()
                .tokenThreshold(context.getTokenThreshold())
                .keepRecentTools(context.getKeepRecentTools())
                .maxToolLength(context.getMaxToolLength())
                .truncationKeepMessages(context.getTruncationKeepMessages())
                .build();
    }
}
