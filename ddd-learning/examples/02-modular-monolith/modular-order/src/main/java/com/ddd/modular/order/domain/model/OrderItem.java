package com.ddd.modular.order.domain.model;

import java.math.BigDecimal;

/** 订单明细（聚合内部）。 */
public record OrderItem(Long productId, String productName, int quantity, BigDecimal unitPrice) {}