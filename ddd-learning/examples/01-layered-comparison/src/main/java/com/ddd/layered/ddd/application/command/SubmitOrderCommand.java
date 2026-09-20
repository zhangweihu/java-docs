package com.ddd.layered.ddd.application.command;

import com.ddd.layered.ddd.domain.model.Money;

import java.util.List;

/**
 * 提交订单命令（CQRS 写侧入参）。
 *
 * <p>命令对象不可变 + 强类型校验，避免 Controller 直接传入杂乱的 Map/JSON。
 */
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