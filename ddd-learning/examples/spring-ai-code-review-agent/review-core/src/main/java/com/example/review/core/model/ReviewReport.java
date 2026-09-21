package com.example.review.core.model;

import java.time.Instant;
import java.util.List;

/**
 * 完整评审报告：所有 SubAgent 评审 + 聚合统计。
 */
public record ReviewReport(
        String repoFullName,
        int prNumber,
        List<ReviewComment> comments,
        ReviewSummary summary,
        Instant reviewedAt,
        long costMillis
) {

    /** 评审汇总统计 */
    public record ReviewSummary(
            int total,
            int blockerCount,
            int criticalCount,
            int majorCount,
            int minorCount,
            int infoCount
    ) {}

    /** 是否应阻塞合并（含 Blocker 或 Critical） */
    public boolean shouldBlockMerge() {
        return summary.blockerCount() > 0 || summary.criticalCount() > 0;
    }
}