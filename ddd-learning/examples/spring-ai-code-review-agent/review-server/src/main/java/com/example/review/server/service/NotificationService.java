package com.example.review.server.service;

import com.example.review.core.model.PullRequestContext;
import com.example.review.core.model.ReviewReport;
import com.example.review.server.config.ReviewProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 通知服务：飞书 / 钉钉 Webhook。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ReviewProperties props;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 严重问题告警（飞书 / 钉钉）。
     */
    public void blockMergeAlert(PullRequestContext ctx, ReviewReport report) {
        String text = """
                🚨 [Code Review] 严重问题 PR
                - 平台: %s
                - 仓库: %s
                - PR: #%d
                - 作者: %s
                - Blocker: %d
                - Critical: %d

                <font color='red'>本次 PR 建议暂缓合并，请人工 Review</font>
                """.formatted(
                ctx.platform(), ctx.repoFullName(), ctx.prNumber(),
                ctx.author(),
                report.summary().blockerCount(),
                report.summary().criticalCount());

        if (props.getNotify().getFeishuWebhook() != null
                && !props.getNotify().getFeishuWebhook().isBlank()) {
            sendFeishu(props.getNotify().getFeishuWebhook(), text);
        }
        if (props.getNotify().getDingtalkWebhook() != null
                && !props.getNotify().getDingtalkWebhook().isBlank()) {
            sendDingtalk(props.getNotify().getDingtalkWebhook(), text);
        }
    }

    private void sendFeishu(String webhook, String text) {
        try {
            String payload = """
                    {"msg_type":"interactive","card":{
                      "header":{"title":{"tag":"plain_text","content":"Code Review Alert"}},
                      "elements":[{"tag":"markdown","content":%s}]
                    }}""".formatted(toJsonString(text));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForObject(webhook, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception e) {
            log.error("飞书通知失败", e);
        }
    }

    private void sendDingtalk(String webhook, String text) {
        try {
            String payload = """
                    {"msgtype":"markdown","markdown":{"title":"Code Review","text":%s}}"""
                    .formatted(toJsonString(text));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForObject(webhook, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception e) {
            log.error("钉钉通知失败", e);
        }
    }

    private String toJsonString(String text) {
        // 简化处理：实际项目应使用 ObjectMapper.writeValueAsString
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}