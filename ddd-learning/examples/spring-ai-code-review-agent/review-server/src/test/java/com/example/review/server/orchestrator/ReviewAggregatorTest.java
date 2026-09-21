package com.example.review.server.orchestrator;

import com.example.review.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReviewAggregator} 单元测试。
 */
class ReviewAggregatorTest {

    private final ReviewAggregator aggregator = new ReviewAggregator();

    @Test
    void shouldDeduplicateSameLocation() {
        PullRequestContext ctx = sample();

        // 同一文件同一行：两条评论（一条 BLOCKER，一条 MAJOR）→ 取 BLOCKER
        List<ReviewComment> comments = List.of(
                new ReviewComment("Security", ReviewSeverity.BLOCKER,
                        "OrderService.java", 11, "SQL 注入", "用参数化"),
                new ReviewComment("Style", ReviewSeverity.MAJOR,
                        "OrderService.java", 11, "格式不对", "改格式")
        );

        ReviewReport report = aggregator.aggregate(ctx, comments, 1000);

        assertThat(report.comments()).hasSize(1);
        assertThat(report.comments().get(0).severity()).isEqualTo(ReviewSeverity.BLOCKER);
        assertThat(report.summary().total()).isEqualTo(1);
        assertThat(report.summary().blockerCount()).isEqualTo(1);
    }

    @Test
    void shouldBlockMergeOnBlockerOrCritical() {
        PullRequestContext ctx = sample();
        List<ReviewComment> comments = List.of(
                new ReviewComment("Security", ReviewSeverity.CRITICAL,
                        "a.java", 1, "严重问题", "建议修复")
        );

        ReviewReport report = aggregator.aggregate(ctx, comments, 100);
        assertThat(report.shouldBlockMerge()).isTrue();
    }

    @Test
    void shouldNotBlockOnMinorOnly() {
        PullRequestContext ctx = sample();
        List<ReviewComment> comments = List.of(
                new ReviewComment("Style", ReviewSeverity.MINOR,
                        "a.java", 1, "风格小问题", "建议调整")
        );

        ReviewReport report = aggregator.aggregate(ctx, comments, 100);
        assertThat(report.shouldBlockMerge()).isFalse();
    }

    @Test
    void shouldComputeSummaryCorrectly() {
        PullRequestContext ctx = sample();
        List<ReviewComment> comments = List.of(
                new ReviewComment("a", ReviewSeverity.BLOCKER, "f1", 1, "m1", "s1"),
                new ReviewComment("a", ReviewSeverity.CRITICAL, "f2", 1, "m2", "s2"),
                new ReviewComment("a", ReviewSeverity.MAJOR, "f3", 1, "m3", "s3"),
                new ReviewComment("a", ReviewSeverity.MINOR, "f4", 1, "m4", "s4"),
                new ReviewComment("a", ReviewSeverity.INFO, "f5", 1, "m5", "s5")
        );

        ReviewReport report = aggregator.aggregate(ctx, comments, 100);

        assertThat(report.summary().total()).isEqualTo(5);
        assertThat(report.summary().blockerCount()).isEqualTo(1);
        assertThat(report.summary().criticalCount()).isEqualTo(1);
        assertThat(report.summary().majorCount()).isEqualTo(1);
        assertThat(report.summary().minorCount()).isEqualTo(1);
        assertThat(report.summary().infoCount()).isEqualTo(1);
    }

    private PullRequestContext sample() {
        return new PullRequestContext(
                "gitlab", "g/p", 1,
                "feat", "main", "alice",
                "test", null, "diff",
                List.of("a.java", "f1", "f2", "f3", "f4", "f5"), 5, 2);
    }
}