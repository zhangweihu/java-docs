package com.ddd.layered.ddd.application.service;

import com.ddd.layered.ddd.application.command.SubmitOrderCommand;
import com.ddd.layered.ddd.domain.model.Order;
import com.ddd.layered.ddd.domain.model.OrderId;
import com.ddd.layered.ddd.domain.model.OrderItem;
import com.ddd.layered.ddd.domain.model.CustomerId;
import com.ddd.layered.ddd.domain.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单应用服务（DDD 应用层）。
 *
 * <p>职责：用例编排 + 事务边界 + 跨聚合协调。
 * <p><b>绝不</b>承载业务规则——业务规则在 {@link Order} 聚合根内。
 */
@Service
public class DddOrderAppService {

    private final OrderRepository orderRepository;

    public DddOrderAppService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderId submit(SubmitOrderCommand cmd) {
        List<OrderItem> items = cmd.items().stream()
            .map(i -> new OrderItem(
                i.productId(),
                i.productName(),
                i.quantity(),
                i.unitPrice()))
            .toList();

        Order order = Order.create(
            OrderId.generate(),
            new CustomerId(cmd.customerId()),
            items
        );

        orderRepository.save(order);
        return order.id();
    }

    @Transactional(readOnly = true)
    public Order findById(OrderId id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + id));
    }
}