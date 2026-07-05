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
    private Context context = new Context();

    @Getter
    @Setter
    public static class Agent {
        private int maxRounds = 1;
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
        private int maxRounds = 1;
        private int toolSemaphorePermits = 1;
    }

    @Getter
    @Setter
    public static class Context {
        private int tokenThreshold = 60000;
        private int keepRecentTools = 4;
        private int maxToolLength = 200;
        private int truncationKeepMessages = 10;
    }
}
