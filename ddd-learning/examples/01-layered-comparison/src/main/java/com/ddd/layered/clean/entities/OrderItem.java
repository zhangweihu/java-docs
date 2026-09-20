package com.ddd.layered.clean.entities;

import java.math.BigDecimal;

/** 整洁架构风格的订单明细实体。 */
public record OrderItem(
    Long productId,
    String productName,
    int quantity,
    BigDecimal unitPrice
) {}