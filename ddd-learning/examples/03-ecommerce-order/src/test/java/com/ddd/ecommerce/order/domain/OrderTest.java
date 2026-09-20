package com.ddd.ecommerce.order.domain;

import com.ddd.ecommerce.order.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单聚合根纯内存单元测试。
 *
 * <p>不起 Spring 容器——这正是 DDD 充血模型的核心价值：业务规则可纯 JUnit 验证。
 */
class OrderTest {

    @Test
    void should_reject_empty_items() {
        assertThrows(IllegalArgumentException.class, () ->
            Order.submit(OrderId.generate(), "O001", new CustomerId(1L), List.of()));
    }

    @Test
    void should_change_status_to_paid_when_pending() {
        Order order = sampleOrder();
        order.pay();
        assertEquals(OrderStatus.PAID, order.status());
    }

    @Test
    void should_reject_double_payment() {
        Order order = sampleOrder();
        order.pay();
        assertThrows(IllegalStateException.class, order::pay);
    }

    @Test
    void should_not_allow_ship_when_not_paid() {
        Order order = sampleOrder();
        assertThrows(IllegalStateException.class, order::ship);
    }

    @Test
    void should_reject_cancel_when_shipped() {
        Order order = sampleOrder();
        order.pay();
        order.ship();
        assertThrows(IllegalStateException.class, () -> order.cancel("用户取消"));
    }

    @Test
    void should_calculate_total_correctly() {
        Order order = sampleOrder();
        // 2 * 100 + 3 * 50 = 350
        assertEquals(new BigDecimal("350.00"), order.total().amount());
    }

    private Order sampleOrder() {
        return Order.submit(
            OrderId.generate(),
            "O" + System.currentTimeMillis(),
            new CustomerId(1L),
            List.of(
                new OrderItem(1L, "商品A", 2,
                    new Money(new BigDecimal("100"), Money.Currency.CNY)),
                new OrderItem(2L, "商品B", 3,
                    new Money(new BigDecimal("50"), Money.Currency.CNY))
            )
        );
    }
}