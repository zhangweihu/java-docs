package com.ddd.layered.ddd.domain;

import com.ddd.layered.ddd.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单聚合根纯内存单元测试。
 *
 * <p>不起 Spring 容器——这正是 DDD 分层的价值，业务规则不依赖任何外部技术。
 *
 * <p>5 个核心场景覆盖：
 * <ol>
 *   <li>创建校验（至少一件商品）</li>
 *   <li>创建校验（金额非空）</li>
 *   <li>支付状态机合法迁移</li>
 *   <li>非法状态迁移（已支付不可再支付）</li>
 *   <li>总价计算正确</li>
 * </ol>
 */
class OrderTest {

    @Test
    void should_reject_empty_items() {
        var id = OrderId.generate();
        var customerId = new CustomerId(1L);
        assertThrows(IllegalArgumentException.class,
            () -> new Order(id, customerId, List.of(), java.time.Instant.now()));
    }

    @Test
    void should_reject_null_unit_price() {
        var item = new OrderItem(1L, "商品A", 1, null);
        assertNotNull(item);  // OrderItem 构造器在 null 时抛异常
        assertThrows(NullPointerException.class,
            () -> new OrderItem(1L, "商品A", 1, null));
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
    void should_calculate_total_correctly() {
        Order order = sampleOrder();
        // 2 * 100 + 3 * 50 = 200 + 150 = 350
        assertEquals(new BigDecimal("350.00"), order.total().amount());
    }

    private Order sampleOrder() {
        Money price1 = new Money(new BigDecimal("100.00"), Money.Currency.CNY);
        Money price2 = new Money(new BigDecimal("50.00"), Money.Currency.CNY);
        List<OrderItem> items = List.of(
            new OrderItem(1L, "商品A", 2, price1),
            new OrderItem(2L, "商品B", 3, price2)
        );
        return Order.create(OrderId.generate(), new CustomerId(1L), items);
    }
}