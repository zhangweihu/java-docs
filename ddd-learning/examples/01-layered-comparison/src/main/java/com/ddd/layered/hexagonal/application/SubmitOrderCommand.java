package com.ddd.layered.hexagonal.application;

import java.math.BigDecimal;
import java.util.List;

/** 六边形风格的提交订单命令（边界参数对象）。 */
public record SubmitOrderCommand(
    Long customerId,
    List<ItemCommand> items
) {
    public record ItemCommand(
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice
    ) {}
}