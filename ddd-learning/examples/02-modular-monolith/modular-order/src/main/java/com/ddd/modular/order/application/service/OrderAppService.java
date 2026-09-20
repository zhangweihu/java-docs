package com.ddd.modular.order.application.service;

import com.ddd.modular.common.exception.BusinessException;
import com.ddd.modular.order.domain.model.Order;
import com.ddd.modular.order.domain.model.OrderItem;
import com.ddd.modular.order.domain.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 订单应用服务。
 *
 * <p>用例：submit / pay / cancel。
 */
@Service
public class OrderAppService {

    private final OrderRepository repository;
    private final ApplicationEventPublisher events;

    public OrderAppService(OrderRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional
    public Long submit(Long customerId, List<OrderItem> items) {
        String orderNo = generateOrderNo();
        Order order = new Order(null, orderNo, customerId, items, Instant.now());
        repository.save(order);
        events.publishEvent(new OrderCreatedEvent(order.id(), orderNo, customerId));
        return order.id();
    }

    @Transactional
    public void pay(Long orderId) {
        Order order = repository.findById(orderId)
            .orElseThrow(() -> new BusinessException.OrderNotFoundException(orderId));
        order.pay();
        repository.save(order);
        events.publishEvent(new OrderPaidEvent(order.id()));
    }

    @Transactional
    public void cancel(Long orderId) {
        Order order = repository.findById(orderId)
            .orElseThrow(() -> new BusinessException.OrderNotFoundException(orderId));
        order.cancel();
        repository.save(order);
        events.publishEvent(new OrderCancelledEvent(order.id()));
    }

    private String generateOrderNo() {
        return "O" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /** 进程内事件。 */
    public record OrderCreatedEvent(Long orderId, String orderNo, Long customerId) {}
    public record OrderPaidEvent(Long orderId) {}
    public record OrderCancelledEvent(Long orderId) {}
}