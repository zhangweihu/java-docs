package com.example.review.server.platform;

import com.example.review.core.model.PullRequestContext;
import com.example.review.core.model.ReviewComment;
import com.example.review.core.model.ReviewReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GitLab MR 平台客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabPlatformClient implements PlatformClient {

    @Value("${gitlab.url:https://gitlab.com}")
    private String gitlabUrl;

    @Value("${gitlab.token:}")
    private String token;

    private GitLabApi api() {
        return new GitLabApi(gitlabUrl, token);
    }

    @Override
    public boolean supports(String platform) {
        return "gitlab".equalsIgnoreCase(platform);
    }

    @Override
    public PullRequestContext fetchContext(String repoFullName, int prNumber) {
        try {
            GitLabApi gitLab = api();
            MergeRequest mr = gitLab.getMergeRequestApi()
                    .getMergeRequest(repoFullName, (long) prNumber);

            // 拉 diff（gitlab4j 返回 String）
            String diff = gitLab.getMergeRequestApi()
                    .getMergeRequestRawDiff(repoFullName, (long) prNumber);

            // 拉变更文件清单
            List<String> files = gitLab.getMergeRequestApi()
                    .getMergeRequestChanges(repoFullName, (long) prNumber)
                    .getChanges().stream()
                    .map(Change::getNewPath)
                    .filter(java.util.Objects::nonNull)
                    .toList();

            return new PullRequestContext(
                    "gitlab", repoFullName, prNumber,
                    mr.getSourceBranch(), mr.getTargetBranch(),
                    mr.getAuthor() != null ? mr.getAuthor().getUsername() : "unknown",
                    mr.getTitle(), mr.getDescription(),
                    diff, files, 0, 0
            );
        } catch (Exception e) {
            throw new RuntimeException("拉取 GitLab MR 失败: " + repoFullName + "#" + prNumber, e);
        }
    }

    @Override
    public void postComments(String repoFullName, int prNumber, ReviewReport report) {
        try {
            GitLabApi gitLab = api();

            // 1. 顶层 issue note（汇总报告）
            gitLab.getMergeRequestApi().createMergeRequestNote(
                    repoFullName, (long) prNumber, formatSummary(report));

            // 2. 每条 inline 评论（GitLab 通过 Discussions API）
            for (ReviewComment c : report.comments()) {
                if (c.line() > 0) {
                    try {
                        String body = "[" + c.severity() + "] **" + c.agent() + "**\n\n"
                                + c.message() + "\n\n**建议**：\n" + c.suggestion();
                        // 调用 Discussions API 创建议论
                        // 注：gitlab4j 的高级 API 可能需用 createMergeRequestDiscussion
                        gitLab.getDiscussionsApi().createMergeRequestDiscussion(
                                repoFullName, (long) prNumber, body);
                    } catch (Exception ex) {
                        log.warn("创建 GitLab inline 讨论失败: {}", ex.getMessage());
                    }
                }
            }

            // 3. commit status（决定是否阻塞合并）
            String sha = gitLab.getMergeRequestApi()
                    .getMergeRequest(repoFullName, (long) prNumber).getSha();
            String state = report.shouldBlockMerge() ? "failed" : "success";
            gitLab.getCommitsApi().addCommitStatus(
                    repoFullName, sha, state,
                    report.summary().total() + " issues", null, "review-agent", false);

        } catch (Exception e) {
            log.error("回写 GitLab 失败", e);
        }
    }

    @Override
    public void setCommitStatus(String repoFullName, String sha,
                                 String state, String description, String targetUrl) {
        try {
            api().getCommitsApi().addCommitStatus(
                    repoFullName, sha, state, description, targetUrl, "review-agent", false);
        } catch (Exception e) {
            log.error("设置 GitLab commit status 失败", e);
        }
    }

    private String formatSummary(ReviewReport r) {
        var s = r.summary();
        return """
                ## 🤖 Code Review Agent 报告

                | Severity | Count |
                |---|---|
                | 🔴 Blocker | %d |
                | 🟠 Critical | %d |
                | 🟡 Major | %d |
                | 🔵 Minor | %d |
                | ⚪ Info | %d |
                | **合计** | **%d** |

                %s

                ---
                ⏱ 审查耗时 %d ms · Agent: %s
                """.formatted(
                s.blockerCount(), s.criticalCount(),
                s.majorCount(), s.minorCount(), s.infoCount(), s.total(),
                r.shouldBlockMerge()
                        ? "⛔ **本次 MR 包含严重问题，建议暂缓合并**"
                        : "✅ 未发现严重问题",
                r.costMillis(),
                "Security / Performance / DDD / TestCoverage / Style");
    }
}