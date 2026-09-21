package com.example.review.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring AI Code Review Agent 启动类。
 *
 * <pre>
 * {@code
 *   # 启动命令
 *   SPRING_PROFILES_ACTIVE=openai OPENAI_API_KEY=sk-xxx mvn spring-boot:run
 * }
 * </pre>
 */
@EnableAsync
@SpringBootApplication(scanBasePackages = {
        "com.example.review.server",
        "com.example.review.agents"
})
public class ReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewApplication.class, args);
    }
}