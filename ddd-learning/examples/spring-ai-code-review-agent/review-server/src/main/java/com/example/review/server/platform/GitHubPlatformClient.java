package com.example.review.server.platform;

import com.example.review.core.model.PullRequestContext;
import com.example.review.core.model.ReviewComment;
import com.example.review.core.model.ReviewReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * GitHub PR 平台客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubPlatformClient implements PlatformClient {

    @Value("${github.token:}")
    private String token;

    private GitHub gh;

    private GitHub gh() throws IOException {
        if (gh == null) {
            gh = new GitHubBuilder().withOAuthToken(token).build();
        }
        return gh;
    }

    @Override
    public boolean supports(String platform) {
        return "github".equalsIgnoreCase(platform);
    }

    @Override
    public PullRequestContext fetchContext(String repoFullName, int prNumber) {
        try {
            GHRepository repo = gh().getRepository(repoFullName);
            GHPullRequest pr = repo.getPullRequest(prNumber);

            String diff = pr.getDiff();
            List<String> files = pr.getFiles().stream()
                    .map(GHFile::getFileName)
                    .toList();

            return new PullRequestContext(
                    "github", repoFullName, prNumber,
                    pr.getHead().getRef(), pr.getBase().getRef(),
                    pr.getUser().getLogin(),
                    pr.getTitle(), pr.getBody(),
                    diff, files, pr.getAdditions(), pr.getDeletions()
            );
        } catch (IOException e) {
            throw new RuntimeException("拉取 GitHub PR 失败: " + repoFullName + "#" + prNumber, e);
        }
    }

    @Override
    public void postComments(String repoFullName, int prNumber, ReviewReport report) {
        try {
            GHPullRequest pr = gh().getRepository(repoFullName).getPullRequest(prNumber);

            // 1. 顶层 issue 评论
            pr.comment(formatSummary(report));

            // 2. inline review comments（精确到行）
            GHPullRequestReviewBuilder builder = pr.createReview();
            for (ReviewComment c : report.comments()) {
                if (c.line() > 0) {
                    String body = "[" + c.severity() + "] **" + c.agent() + "**\n\n"
                            + c.message() + "\n\n**建议**：\n" + c.suggestion();
                    builder.comment(body, c.file(), c.line());
                }
            }
            builder.create();

            // 3. commit status
            String state = report.shouldBlockMerge()
                    ? GHCommitState.ERROR : GHCommitState.SUCCESS;
            pr.getRepository().createCommitStatus(
                    pr.getHead().getSha(), state,
                    report.summary().total() + " issues",
                    "review-agent", "agent-report");

        } catch (IOException e) {
            log.error("回写 GitHub 失败", e);
        }
    }

    @Override
    public void setCommitStatus(String repoFullName, String sha,
                                 String state, String description, String targetUrl) {
        try {
            GHRepository repo = gh().getRepository(repoFullName);
            GHCommitState ghState = switch (state) {
                case "success" -> GHCommitState.SUCCESS;
                case "pending" -> GHCommitState.PENDING;
                default -> GHCommitState.ERROR;
            };
            repo.createCommitStatus(sha, ghState, description, targetUrl, "review-agent");
        } catch (IOException e) {
            log.error("设置 GitHub commit status 失败", e);
        }
    }

    private String formatSummary(ReviewReport r) {
        var s = r.summary();
        return """
                ## 🤖 Code Review Agent Report

                | Severity | Count |
                |---|---|
                | 🔴 Blocker | %d |
                | 🟠 Critical | %d |
                | 🟡 Major | %d |
                | 🔵 Minor | %d |
                | ⚪ Info | %d |

                %s

                ---
                ⏱ %d ms · Agents: Security / Performance / DDD / TestCoverage / Style
                """.formatted(
                s.blockerCount(), s.criticalCount(),
                s.majorCount(), s.minorCount(), s.infoCount(),
                r.shouldBlockMerge()
                        ? "⛔ **This PR has critical issues**"
                        : "✅ No critical issues found",
                r.costMillis());
    }
}