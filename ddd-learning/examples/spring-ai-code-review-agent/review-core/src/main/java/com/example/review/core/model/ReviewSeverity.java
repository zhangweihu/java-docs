package com.example.review.core.model;

/**
 * 评审意见的严重度。
 */
public enum ReviewSeverity {
    BLOCKER,    // 阻塞合并：致命（安全漏洞、数据丢失）
    CRITICAL,   // 严重（性能、并发、安全）
    MAJOR,      // 重要（DDD 边界、事务边界）
    MINOR,      // 次要（风格、可读性）
    INFO;       // 提示

    public boolean isBlocker() {
        return this == BLOCKER || this == CRITICAL;
    }
}