package com.ddd.ecommerce.order.application.command;

import com.ddd.ecommerce.order.domain.model.Money;

import java.util.List;

/** 提交订单命令（CQRS 写侧入参）。 */
public record SubmitOrderCommand(
    Long customerId,
    List<OrderItemCommand> items
) {
    public record OrderItemCommand(
        Long productId,
        String productName,
        int quantity,
        Money unitPrice
    ) {}
}