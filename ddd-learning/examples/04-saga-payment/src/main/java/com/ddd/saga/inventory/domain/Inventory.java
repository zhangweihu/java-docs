package com.ddd.saga.inventory.domain;

/**
 * 库存聚合（Saga 集成版）。
 *
 * <p>三态模型：available / reserved / sold。
 */
public class Inventory {

    private final Long productId;
    private int available;
    private int reserved;
    private int sold;

    public Inventory(Long productId, int initialQuantity) {
        if (initialQuantity < 0) throw new IllegalArgumentException("初始库存不能为负");
        this.productId = productId;
        this.available = initialQuantity;
        this.reserved = 0;
        this.sold = 0;
    }

    public void reserve(int quantity) {
        if (available < quantity) {
            throw new IllegalStateException("库存不足：" + productId);
        }
        this.available -= quantity;
        this.reserved += quantity;
    }

    public void commit(int quantity) {
        if (reserved < quantity) throw new IllegalStateException("预留不足");
        this.reserved -= quantity;
        this.sold += quantity;
    }

    public void release(int quantity) {
        if (reserved < quantity) throw new IllegalStateException("预留不足");
        this.reserved -= quantity;
        this.available += quantity;
    }

    public Long productId() { return productId; }
    public int available() { return available; }
    public int reserved() { return reserved; }
    public int sold() { return sold; }
}