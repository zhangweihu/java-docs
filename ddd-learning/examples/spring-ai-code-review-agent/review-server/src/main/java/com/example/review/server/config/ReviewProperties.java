package com.example.review.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 评审相关配置（注入到 application.yml 的 review.* 配置）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "review")
public class ReviewProperties {

    /** 启用的 Agent 列表 */
    private List<String> enabledAgents = List.of(
            "Security", "Performance", "DDD", "TestCoverage", "Style");

    /** 单 Agent 评审超时（秒） */
    private int timeoutSeconds = 120;

    /** 大 diff 截断字节数 */
    private int diffTruncateBytes = 50_000;

    /** 触发阻塞合并的严重度列表 */
    private List<String> blockOn = List.of("BLOCKER", "CRITICAL");

    /** 通知配置 */
    private Notify notify = new Notify();

    @Data
    public static class Notify {
        private String feishuWebhook = "";
        private String dingtalkWebhook = "";
    }
}