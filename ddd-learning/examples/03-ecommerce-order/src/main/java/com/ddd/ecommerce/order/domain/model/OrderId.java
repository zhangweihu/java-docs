package com.ddd.ecommerce.order.domain.model;

import java.util.Objects;
import java.util.UUID;

/** 订单标识值对象。 */
public record OrderId(Long value) {

    public OrderId {
        Objects.requireNonNull(value, "OrderId 不能为空");
        if (value <= 0) {
            throw new IllegalArgumentException("OrderId 必须为正数");
        }
    }

    public static OrderId generate() {
        return new OrderId(Math.abs(UUID.randomUUID().hashCode() % 1_000_000_000L) + 1L);
    }
}