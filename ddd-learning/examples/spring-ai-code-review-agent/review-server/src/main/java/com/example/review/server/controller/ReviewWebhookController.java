package com.example.review.server.controller;

import com.example.review.core.model.PullRequestContext;
import com.example.review.server.orchestrator.ReviewOrchestrator;
import com.example.review.server.platform.PlatformClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Webhook 入口：
 * <ul>
 *   <li>GitLab: {@code POST /webhook/gitlab}</li>
 *   <li>GitHub: {@code POST /webhook/github}</li>
 * </ul>
 *
 * 配置示例：
 * <ul>
 *   <li>GitLab → Settings → Webhooks → URL: &lt;host&gt;/webhook/gitlab，Trigger: Merge request events</li>
 *   <li>GitHub → Settings → Webhooks → URL: &lt;host&gt;/webhook/github，Events: Pull requests</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class ReviewWebhookController {

    private final List<PlatformClient> platformClients;
    private final ReviewOrchestrator orchestrator;

    /**
     * GitLab Webhook 入口。
     */
    @PostMapping("/gitlab")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> onGitLab(
            @RequestBody GitLabWebhookPayload payload,
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token) {
        log.debug("收到 GitLab webhook: object_kind={}", payload.object_kind());

        if (!"merge_request".equals(payload.object_kind())) {
            return Map.of("status", "ignored", "reason", "not merge_request");
        }
        String action = payload.object_attributes().action();
        if (!List.of("open", "reopen", "update").contains(action)) {
            return Map.of("status", "ignored", "reason", "action=" + action);
        }

        String repoFullName = payload.project().path_with_namespace();
        int prNumber = payload.object_attributes().iid();

        PlatformClient client = pickPlatform("gitlab");
        PullRequestContext ctx = client.fetchContext(repoFullName, prNumber);
        orchestrator.review(ctx);

        return Map.of("status", "queued", "platform", "gitlab",
                "repo", repoFullName, "pr", prNumber);
    }

    /**
     * GitHub Webhook 入口。
     */
    @PostMapping("/github")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> onGitHub(@RequestBody GitHubWebhookPayload payload) {
        log.debug("收到 GitHub webhook: action={}", payload.action());

        String action = payload.action();
        if (!List.of("opened", "synchronize", "reopened").contains(action)) {
            return Map.of("status", "ignored", "reason", "action=" + action);
        }

        String repoFullName = payload.repository().full_name();
        int prNumber = payload.pull_request().number();

        PlatformClient client = pickPlatform("github");
        PullRequestContext ctx = client.fetchContext(repoFullName, prNumber);
        orchestrator.review(ctx);

        return Map.of("status", "queued", "platform", "github",
                "repo", repoFullName, "pr", prNumber);
    }

    /**
     * 健康检查（供 GitLab/GitHub ping 测试）。
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    private PlatformClient pickPlatform(String platform) {
        return platformClients.stream()
                .filter(c -> c.supports(platform))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("不支持的平台: " + platform));
    }

    // ===== Webhook Payload DTO（精简，按需补全）=====

    public record GitLabWebhookPayload(
            String object_kind,
            GitLabProject project,
            GitLabMR object_attributes) {}

    public record GitLabProject(String path_with_namespace) {}

    public record GitLabMR(
            String action,
            int iid,
            String title,
            String description,
            String source_branch,
            String target_branch,
            Author author) {}

    public record Author(String username) {}

    public record GitHubWebhookPayload(
            String action,
            GitHubRepo repository,
            GitHubPR pull_request) {}

    public record GitHubRepo(String full_name) {}

    public record GitHubPR(
            int number,
            String title,
            String body) {}
}