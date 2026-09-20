package com.ddd.ecommerce.order.domain.model;

/**
 * 订单状态枚举。
 *
 * <p>状态机迁移规则：
 * <pre>
 *   PENDING ──pay──► PAID ──ship──► SHIPPED ──complete──► COMPLETED
 *      │             │
 *      │             └─refund──► REFUNDED
 *      │
 *      └──cancel──► CANCELLED
 * </pre>
 */
public enum OrderStatus {
    PENDING,    // 待支付
    PAID,       // 已支付
    SHIPPED,    // 已发货
    COMPLETED,  // 已完成
    CANCELLED,  // 已取消
    REFUNDED    // 已退款
}