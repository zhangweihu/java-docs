package com.example.review.core.model;

import java.util.List;

/**
 * Pull Request 上下文：diff + 元数据 + 受影响文件清单。
 * 由 PlatformClient 在 Webhook 触发时填充。
 *
 * @param platform        平台：gitlab / github
 * @param repoFullName    仓库全名（GitLab: group/project，GitHub: owner/repo）
 * @param prNumber        PR/MR 编号
 * @param sourceBranch    源分支
 * @param targetBranch    目标分支
 * @param author          作者 username
 * @param title           标题
 * @param description     描述
 * @param diff            完整 diff 文本
 * @param changedFiles    受影响文件路径列表
 * @param additions       新增行数
 * @param deletions       删除行数
 */
public record PullRequestContext(
        String platform,
        String repoFullName,
        int prNumber,
        String sourceBranch,
        String targetBranch,
        String author,
        String title,
        String description,
        String diff,
        List<String> changedFiles,
        int additions,
        int deletions
) {
    /** 是否为大 PR（diff 超 100KB） */
    public boolean isLarge() {
        return diff != null && diff.length() > 100_000;
    }
}