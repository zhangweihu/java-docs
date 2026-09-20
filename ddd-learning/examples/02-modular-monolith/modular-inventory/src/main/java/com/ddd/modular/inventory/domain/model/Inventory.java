package com.ddd.modular.inventory.domain.model;

import java.time.Instant;

/**
 * 库存聚合（三态模型：可用 / 预留 / 已售）。
 *
 * <p>不变量：available + reserved + sold = 总库存；任何单字段 ≥ 0。
 * <p>行为：reserve / commit / release。
 */
public class Inventory {

    private final Long productId;
    private int available;
    private int reserved;
    private int sold;
    private Instant updatedAt;

    public Inventory(Long productId, int total) {
        if (total < 0) throw new IllegalArgumentException("总库存不能为负");
        this.productId = productId;
        this.available = total;
        this.reserved = 0;
        this.sold = 0;
        this.updatedAt = Instant.now();
    }

    public Inventory(Long productId, int available, int reserved, int sold, Instant updatedAt) {
        if (available < 0 || reserved < 0 || sold < 0) {
            throw new IllegalArgumentException("库存字段不能为负");
        }
        this.productId = productId;
        this.available = available;
        this.reserved = reserved;
        this.sold = sold;
        this.updatedAt = updatedAt;
    }

    /** 预留：available → reserved。 */
    public void reserve(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("预留数量必须为正");
        if (available < quantity) {
            throw new com.ddd.modular.common.exception.BusinessException
                .InsufficientInventoryException(productId);
        }
        this.available -= quantity;
        this.reserved += quantity;
        this.updatedAt = Instant.now();
    }

    /** 扣减：reserved → sold（订单付款成功后调用）。 */
    public void commit(int quantity) {
        if (reserved < quantity) {
            throw new IllegalStateException("预留数量不足，无法扣减");
        }
        this.reserved -= quantity;
        this.sold += quantity;
        this.updatedAt = Instant.now();
    }

    /** 释放：reserved → available（订单取消时调用）。 */
    public void release(int quantity) {
        if (reserved < quantity) {
            throw new IllegalStateException("预留数量不足，无法释放");
        }
        this.reserved -= quantity;
        this.available += quantity;
        this.updatedAt = Instant.now();
    }

    public Long productId() { return productId; }
    public int available() { return available; }
    public int reserved() { return reserved; }
    public int sold() { return sold; }
    public Instant updatedAt() { return updatedAt; }
}