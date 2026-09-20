package com.ddd.layered.clean.entities;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 整洁架构风格的 Order 实体（充血）。
 *
 * <p>对比 DDD 四层的 Order：
 * <ul>
 *   <li>DDD 用 {@code OrderId}/{@code CustomerId} 强类型标识</li>
 *   <li>Clean 用原始 {@code Long}（简化版）</li>
 * </ul>
 *
 * <p>整洁架构并不强制强类型 ID——它只关心"依赖方向"。
 */
public class Order {

    private final Long id;
    private final Long customerId;
    private OrderStatus status;
    private final Instant createdAt;
    private final List<OrderItem> items = new ArrayList<>();

    public static Order create(Long customerId, List<OrderItem> items) {
        return new Order(System.currentTimeMillis(), customerId, items, Instant.now());
    }

    public Order(Long id, Long customerId, List<OrderItem> items, Instant createdAt) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("订单至少一件商品");
        }
        this.id = id;
        this.customerId = customerId;
        this.items.addAll(items);
        this.createdAt = createdAt;
        this.status = OrderStatus.PENDING;
    }

    public void pay() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("仅 PENDING 状态可支付");
        }
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("已发货订单不可取消");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public Long id() { return id; }
    public Long customerId() { return customerId; }
    public OrderStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public List<OrderItem> items() { return Collections.unmodifiableList(items); }

    public enum OrderStatus { PENDING, PAID, SHIPPED, COMPLETED, CANCELLED }
}