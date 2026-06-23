package com.genchat;

import com.genchat.config.GenChatProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.genchat.repository")
@EnableConfigurationProperties(GenChatProperties.class)
public class GenChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenChatApplication.class, args);
    }
}
