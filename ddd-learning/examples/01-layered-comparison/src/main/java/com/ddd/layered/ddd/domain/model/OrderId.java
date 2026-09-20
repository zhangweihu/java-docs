package com.ddd.layered.ddd.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 订单标识值对象（强类型 ID）。
 *
 * <p>使用 {@code Long} 作为底层类型，但包装成强类型后能避免"两个 Long 互相赋值"的误用。
 *
 * <p>充血实体必须只持 {@code OrderId}，不直接持 {@code Long}。
 */
public record OrderId(Long value) {

    public OrderId {
        Objects.requireNonNull(value, "OrderId 不能为空");
    }

    /** 生成新订单 ID（基于雪花算法的简化版：UUID hashCode）。 */
    public static OrderId generate() {
        return new OrderId(Math.abs(UUID.randomUUID().hashCode() % 1_000_000_000L) + 1L);
    }

    @Override
    public String toString() {
        return "Order#" + value;
    }
}