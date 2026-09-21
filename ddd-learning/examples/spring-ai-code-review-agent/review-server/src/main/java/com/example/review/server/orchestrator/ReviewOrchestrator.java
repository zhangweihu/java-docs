package com.example.review.server.orchestrator;

import com.example.review.core.agent.CodeReviewAgent;
import com.example.review.core.model.PullRequestContext;
import com.example.review.core.model.ReviewComment;
import com.example.review.core.model.ReviewReport;
import com.example.review.server.platform.PlatformClient;
import com.example.review.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * 评审编排器：并行调度所有 SubAgent → 聚合 → 写回平台 → 通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewOrchestrator {

    private final List<CodeReviewAgent> agents;
    private final List<PlatformClient> platformClients;
    private final ReviewAggregator aggregator;
    private final NotificationService notifier;

    @Value("${review.timeout-seconds:120}")
    private int timeoutSeconds;

    /**
     * 核心入口：评审一个 PR 上下文，并写回结果。
     * 返回 CompletableFuture 方便测试和上层等待。
     */
    @Async("reviewExecutor")
    public CompletableFuture<ReviewReport> review(PullRequestContext ctx) {
        log.info("[Review Start] {}/{} PR#{}", ctx.platform(), ctx.repoFullName(), ctx.prNumber());
        long start = System.currentTimeMillis();

        // 1. 并行调度所有 Agent
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(agents.size(), 5));
        try {
            List<CompletableFuture<List<ReviewComment>>> futures = agents.stream()
                    .map(agent -> CompletableFuture
                            .supplyAsync(() -> safeReview(agent, ctx), pool)
                            .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                            .exceptionally(ex -> {
                                log.error("Agent {} 审查失败", agent.name(), ex);
                                return List.of();
                            }))
                    .toList();

            // 2. 等待所有 Agent 完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(timeoutSeconds * 2L, TimeUnit.SECONDS)
                    .join();

            List<ReviewComment> allComments = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .toList();

            // 3. 聚合
            long costMillis = System.currentTimeMillis() - start;
            ReviewReport report = aggregator.aggregate(ctx, allComments, costMillis);

            log.info("[Review End] {}/{} PR#{} - {} comments, blocker={}, critical={}",
                    ctx.platform(), ctx.repoFullName(), ctx.prNumber(),
                    report.summary().total(),
                    report.summary().blockerCount(),
                    report.summary().criticalCount());

            // 4. 异步写回平台
            writeBackAsync(ctx, report);

            return CompletableFuture.completedFuture(report);

        } catch (Exception e) {
            log.error("评审流程异常", e);
            return CompletableFuture.failedFuture(e);
        } finally {
            pool.shutdown();
        }
    }

    private List<ReviewComment> safeReview(CodeReviewAgent agent, PullRequestContext ctx) {
        try {
            return agent.review(ctx);
        } catch (Exception e) {
            log.error("Agent {} 异常", agent.name(), e);
            return List.of();
        }
    }

    private void writeBackAsync(PullRequestContext ctx, ReviewReport report) {
        CompletableFuture.runAsync(() -> {
            try {
                PlatformClient client = platformClients.stream()
                        .filter(c -> c.supports(ctx.platform()))
                        .findFirst().orElse(null);
                if (client == null) {
                    log.warn("未找到匹配 {} 平台的客户端", ctx.platform());
                    return;
                }
                client.postComments(ctx.repoFullName(), ctx.prNumber(), report);

                if (report.shouldBlockMerge()) {
                    notifier.blockMergeAlert(ctx, report);
                }
            } catch (Exception e) {
                log.error("写回平台失败", e);
            }
        });
    }
}