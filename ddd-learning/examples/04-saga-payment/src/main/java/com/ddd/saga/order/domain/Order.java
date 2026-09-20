package com.ddd.saga.order.domain;

import java.time.Instant;

/**
 * 订单聚合根（精简版）。
 *
 * <p>Saga 示例中的订单不展开完整 DDD 战术细节，聚焦于：
 * <ul>
 *   <li>4 个状态（PENDING / PAID / SHIPPED / CANCELLED）</li>
 *   <li>状态机迁移</li>
 *   <li>Saga ID 关联（用于 Saga 协调）</li>
 * </ul>
 */
public class Order {

    private final Long id;
    private final String orderNo;
    private final Long customerId;
    private final java.math.BigDecimal amount;
    private OrderStatus status;
    private String sagaId;
    private final Instant createdAt;

    public Order(Long id, String orderNo, Long customerId,
                 java.math.BigDecimal amount, Instant createdAt) {
        this.id = id;
        this.orderNo = orderNo;
        this.customerId = customerId;
        this.amount = amount;
        this.createdAt = createdAt;
        this.status = OrderStatus.PENDING;
    }

    public void markPaid() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("订单当前状态不允许支付：" + status);
        }
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalStateException("已发货订单不可取消");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void ship() { this.status = OrderStatus.SHIPPED; }

    public Long id() { return id; }
    public String orderNo() { return orderNo; }
    public Long customerId() { return customerId; }
    public java.math.BigDecimal amount() { return amount; }
    public OrderStatus status() { return status; }
    public String sagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public Instant createdAt() { return createdAt; }

    public enum OrderStatus { PENDING, PAID, SHIPPED, CANCELLED }
}