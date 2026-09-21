package com.example.review.server.orchestrator;

import com.example.review.core.model.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * 聚合器：合并多个 SubAgent 结果 + 去重 + 严重度统计。
 */
@Component
public class ReviewAggregator {

    /**
     * 聚合：同位置多 Agent 报同一问题 → 取最严重。
     */
    public ReviewReport aggregate(PullRequestContext ctx,
                                   List<ReviewComment> comments,
                                   long costMillis) {
        Map<String, ReviewComment> dedup = new LinkedHashMap<>();
        for (ReviewComment c : comments) {
            String key = c.file() + ":" + c.line() + ":" + truncate(c.message(), 50);
            ReviewComment existing = dedup.get(key);
            if (existing == null || c.severity().ordinal() < existing.severity().ordinal()) {
                dedup.put(key, c);
            }
        }

        List<ReviewComment> unique = new ArrayList<>(dedup.values());

        // 严重度统计
        int blocker = 0, critical = 0, major = 0, minor = 0, info = 0;
        for (ReviewComment c : unique) {
            switch (c.severity()) {
                case BLOCKER -> blocker++;
                case CRITICAL -> critical++;
                case MAJOR -> major++;
                case MINOR -> minor++;
                case INFO -> info++;
            }
        }

        ReviewReport.ReviewSummary summary = new ReviewReport.ReviewSummary(
                unique.size(), blocker, critical, major, minor, info);

        return new ReviewReport(
                ctx.repoFullName(), ctx.prNumber(), unique, summary,
                Instant.now(), costMillis);
    }

    private String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n));
    }
}