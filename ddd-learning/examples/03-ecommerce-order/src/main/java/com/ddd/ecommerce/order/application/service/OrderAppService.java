package com.ddd.ecommerce.order.application.service;

import com.ddd.ecommerce.order.application.command.SubmitOrderCommand;
import com.ddd.ecommerce.order.domain.model.*;
import com.ddd.ecommerce.order.domain.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 订单应用服务。
 *
 * <p>5 个用例：submit / pay / cancel / ship / complete + findById
 * <p>每个方法 = 一个事务边界 = 一个用例
 * <p>业务规则全部在 {@link Order} 聚合根内
 */
@Service
public class OrderAppService {

    private final OrderRepository repository;
    private final ApplicationEventPublisher events;

    public OrderAppService(OrderRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    /** 用例 1：创建订单。 */
    @Transactional
    public OrderId submit(SubmitOrderCommand cmd) {
        List<OrderItem> items = cmd.items().stream()
            .map(i -> new OrderItem(
                i.productId(), i.productName(), i.quantity(), i.unitPrice()))
            .toList();

        Order order = Order.submit(
            OrderId.generate(),
            generateOrderNo(),
            new CustomerId(cmd.customerId()),
            items
        );

        repository.save(order);
        events.publishEvent(new OrderCreatedEvent(order.id(), order.orderNo(), order.customerId().value(), Instant.now()));
        return order.id();
    }

    /** 用例 2：支付订单。 */
    @Transactional
    public void pay(OrderId orderId) {
        Order order = repository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderId));
        order.pay();
        repository.save(order);
        events.publishEvent(new OrderPaidEvent(order.id(), Instant.now()));
    }

    /** 用例 3：取消订单。 */
    @Transactional
    public void cancel(OrderId orderId, String reason) {
        Order order = repository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderId));
        order.cancel(reason);
        repository.save(order);
        events.publishEvent(new OrderCancelledEvent(order.id(), reason, Instant.now()));
    }

    /** 用例 4：发货。 */
    @Transactional
    public void ship(OrderId orderId) {
        Order order = repository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderId));
        order.ship();
        repository.save(order);
        events.publishEvent(new OrderShippedEvent(order.id(), Instant.now()));
    }

    /** 用例 5：完成订单。 */
    @Transactional
    public void complete(OrderId orderId) {
        Order order = repository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderId));
        order.complete();
        repository.save(order);
        events.publishEvent(new OrderCompletedEvent(order.id(), Instant.now()));
    }

    /** 用例 6：查询订单。 */
    @Transactional(readOnly = true)
    public Order findById(OrderId orderId) {
        return repository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderId));
    }

    private String generateOrderNo() {
        return "O" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    // ===== 领域事件 =====

    public record OrderCreatedEvent(OrderId orderId, String orderNo, Long customerId, Instant occurredAt) {}
    public record OrderPaidEvent(OrderId orderId, Instant occurredAt) {}
    public record OrderCancelledEvent(OrderId orderId, String reason, Instant occurredAt) {}
    public record OrderShippedEvent(OrderId orderId, Instant occurredAt) {}
    public record OrderCompletedEvent(OrderId orderId, Instant occurredAt) {}
}