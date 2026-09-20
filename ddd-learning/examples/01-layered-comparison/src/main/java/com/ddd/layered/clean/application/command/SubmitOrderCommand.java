package com.ddd.layered.clean.application.command;

import java.math.BigDecimal;
import java.util.List;

/** 整洁架构风格的提交订单命令。 */
public record SubmitOrderCommand(
    Long customerId,
    List<ItemCommand> items
) {
    public record ItemCommand(
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        String currency
    ) {}
}