package com.genchat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "genchat")
public class GenChatProperties {

    private Agent agent = new Agent();
    private File file = new File();
    private DeepResearch deepResearch = new DeepResearch();

    @Getter
    @Setter
    public static class Agent {
        private int maxRounds = 5;
        private int maxRetries = 0;
        private int chatMemorySize = 30;
    }

    @Getter
    @Setter
    public static class File {
        private int chunkSize = 500;
        private int chunkOverlap = 50;
    }

    @Getter
    @Setter
    public static class DeepResearch {
        private int maxRounds = 3;
        private int toolSemaphorePermits = 3;
    }
}
