package com.ddd.modular.order.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 订单聚合根（充血）。
 *
 * <p>模块：{@code modular-order}
 * <p>对外：通过 {@code application.api.OrderQueryService} 暴露视图。
 */
public class Order {

    private final Long id;
    private final String orderNo;
    private final Long customerId;
    private OrderStatus status;
    private final Instant createdAt;
    private final List<OrderItem> items = new ArrayList<>();

    public Order(Long id, String orderNo, Long customerId, List<OrderItem> items, Instant createdAt) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("订单至少一件商品");
        }
        this.id = id;
        this.orderNo = orderNo;
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
    public String orderNo() { return orderNo; }
    public Long customerId() { return customerId; }
    public OrderStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public List<OrderItem> items() { return Collections.unmodifiableList(items); }

    public enum OrderStatus { PENDING, PAID, SHIPPED, COMPLETED, CANCELLED }
}