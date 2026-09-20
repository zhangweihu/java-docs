package com.ddd.layered.hexagonal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 六边形架构风格的 Order 实体（业务核心）。
 *
 * <p>位于 {@code domain} 包，对外暴露：
 * <ul>
 *   <li>入口端口（{@code ports.in.SubmitOrderPort}）—— 业务核心能"做什么"</li>
 *   <li>出口端口（{@code ports.out.OrderRepositoryPort}）—— 业务核心需要"什么外部能力"</li>
 * </ul>
 *
 * <p>本类只依赖 JDK + 自家值对象，不感知任何外部技术（MyBatis、Spring、HTTP）。
 */
public class Order {

    private final Long id;
    private final Long customerId;
    private OrderStatus status;
    private final Instant createdAt;
    private final List<OrderItem> items = new ArrayList<>();

    public static Order create(Long customerId, List<OrderItem> items) {
        return new Order(System.nanoTime(), customerId, items, Instant.now());
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
        enforceStatus(OrderStatus.PENDING, "支付");
        this.status = OrderStatus.PAID;
    }

    private void enforceStatus(OrderStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("当前状态" + status + "不允许" + action);
        }
    }

    public Long id() { return id; }
    public Long customerId() { return customerId; }
    public OrderStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public List<OrderItem> items() { return Collections.unmodifiableList(items); }

    public enum OrderStatus { PENDING, PAID, SHIPPED, COMPLETED, CANCELLED }

    /** 订单明细（内嵌）。 */
    public record OrderItem(Long productId, String productName,
                            int quantity, BigDecimal unitPrice) {}
}