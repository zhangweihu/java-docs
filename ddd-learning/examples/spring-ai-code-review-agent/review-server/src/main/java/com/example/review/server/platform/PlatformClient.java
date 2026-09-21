package com.example.review.server.platform;

import com.example.review.core.model.PullRequestContext;
import com.example.review.core.model.ReviewReport;

/**
 * 平台客户端抽象接口。
 * <p>
 * 实现类：GitLabPlatformClient、GitHubPlatformClient。
 */
public interface PlatformClient {

    /** 是否支持该平台（"gitlab" / "github"） */
    boolean supports(String platform);

    /** 拉取 PR 上下文（diff + 元数据 + 受影响文件） */
    PullRequestContext fetchContext(String repoFullName, int prNumber);

    /** 把评审意见写到 PR/MR 评论 */
    void postComments(String repoFullName, int prNumber, ReviewReport report);

    /** 设置 commit status（success / failed / pending） */
    void setCommitStatus(String repoFullName, String sha,
                          String state, String description, String targetUrl);
}