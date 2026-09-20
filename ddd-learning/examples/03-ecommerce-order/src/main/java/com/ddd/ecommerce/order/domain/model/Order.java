package com.ddd.ecommerce.order.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 订单聚合根（充血实现）。
 *
 * <p>核心不变量：
 * <ul>
 *   <li>至少一件商品</li>
 *   <li>状态机约束（见 {@link OrderStatus}）</li>
 *   <li>对外仅暴露不可变视图</li>
 * </ul>
 *
 * <p>领域层**绝对**不依赖 Spring、MyBatis、JPA。
 */
public class Order {

    private final OrderId id;
    private final String orderNo;
    private final CustomerId customerId;
    private OrderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<OrderItem> items = new ArrayList<>();

    /** 工厂方法：创建新订单。 */
    public static Order submit(OrderId id, String orderNo,
                               CustomerId customerId, List<OrderItem> items) {
        return new Order(id, orderNo, customerId, items, Instant.now());
    }

    /** 仓储层重建（含 createdAt + updatedAt）。 */
    public Order(OrderId id, String orderNo, CustomerId customerId,
                 List<OrderItem> items, Instant createdAt) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("订单至少一件商品");
        }
        this.id = id;
        this.orderNo = orderNo;
        this.customerId = customerId;
        this.items.addAll(items);
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = OrderStatus.PENDING;
    }

    // ===== 行为方法 =====

    public void pay() {
        enforceStatus(OrderStatus.PENDING, "支付");
        this.status = OrderStatus.PAID;
        this.updatedAt = Instant.now();
    }

    public void ship() {
        enforceStatus(OrderStatus.PAID, "发货");
        this.status = OrderStatus.SHIPPED;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        enforceStatus(OrderStatus.SHIPPED, "完成");
        this.status = OrderStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("已发货/已完成订单不可取消");
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public Money total() {
        return items.stream()
                    .map(OrderItem::subtotal)
                    .reduce(Money.ZERO, Money::add);
    }

    // ===== 不变量校验 =====

    private void enforceStatus(OrderStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                "订单" + orderNo + "当前状态" + status + "不允许" + action);
        }
    }

    // ===== Getter（只读视图） =====

    public OrderId id() { return id; }
    public String orderNo() { return orderNo; }
    public CustomerId customerId() { return customerId; }
    public OrderStatus status() { return status; }
    public List<OrderItem> items() { return Collections.unmodifiableList(items); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}