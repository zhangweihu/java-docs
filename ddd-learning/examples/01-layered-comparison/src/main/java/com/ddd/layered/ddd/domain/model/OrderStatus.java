package com.ddd.layered.ddd.domain.model;

/**
 * 订单状态枚举。
 *
 * <p>状态机迁移规则见 {@link Order}：
 * <pre>
 *   PENDING → PAID → SHIPPED → COMPLETED
 *      │
 *      └─► CANCELLED
 * </pre>
 */
public enum OrderStatus {
    PENDING,    // 待支付
    PAID,       // 已支付
    SHIPPED,    // 已发货
    COMPLETED,  // 已完成
    CANCELLED   // 已取消
}