package com.ddd.layered.ddd.domain.model;

import java.util.Objects;

/**
 * 订单明细（聚合内部实体）。
 *
 * <p>由 {@link Order} 完全管理，对外不可见。
 */
public final class OrderItem {

    private final Long productId;
    private final String productName;
    private final int quantity;
    private final Money unitPrice;

    public OrderItem(Long productId, String productName, int quantity, Money unitPrice) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("商品 ID 必须为正数");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量必须为正数");
        }
        Objects.requireNonNull(unitPrice, "单价不能为空");

        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    public Long productId() { return productId; }
    public String productName() { return productName; }
    public int quantity() { return quantity; }
    public Money unitPrice() { return unitPrice; }
}