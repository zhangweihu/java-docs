package com.ddd.layered.ddd.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 订单聚合根（充血实现）。
 *
 * <p>核心不变量：
 * <ul>
 *   <li>至少一件商品</li>
 *   <li>状态机约束（见 {@link OrderStatus}）</li>
 *   <li>对外仅暴露不可变视图（{@code List.copyOf}）</li>
 * </ul>
 *
 * <p>领域层**绝对**不依赖 Spring、MyBatis、JPA。
 */
public class Order {

    private final OrderId id;
    private final CustomerId customerId;
    private final Instant createdAt;
    private OrderStatus status;
    private final List<OrderItem> items = new ArrayList<>();

    /** 工厂方法：创建新订单。 */
    public static Order create(OrderId id, CustomerId customerId, List<OrderItem> items) {
        return new Order(id, customerId, items, Instant.now());
    }

    /** 仓储层重建聚合根时使用（含 createdAt）。 */
    public Order(OrderId id, CustomerId customerId, List<OrderItem> items, Instant createdAt) {
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

    public void ship() {
        enforceStatus(OrderStatus.PAID, "发货");
        this.status = OrderStatus.SHIPPED;
    }

    public void complete() {
        enforceStatus(OrderStatus.SHIPPED, "完成");
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("已发货/已完成订单不可取消");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public Money total() {
        return items.stream()
                    .map(OrderItem::subtotal)
                    .reduce(Money.ZERO, Money::add);
    }

    private void enforceStatus(OrderStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                "订单当前状态" + status + "不允许" + action);
        }
    }

    public OrderId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public OrderStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public List<OrderItem> items() { return Collections.unmodifiableList(items); }
}