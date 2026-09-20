package com.ddd.layered.hexagonal.application;

import com.ddd.layered.hexagonal.domain.Order;
import com.ddd.layered.hexagonal.ports.in.SubmitOrderPort;
import com.ddd.layered.hexagonal.ports.out.OrderRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 业务核心实现（实现入口端口）。
 *
 * <p>六边形与 DDD 的关键差异：
 * <ul>
 *   <li>DDD {@code AppService}：直接被 Controller 注入</li>
 *   <li>Hex {@code *Service}：实现 {@code Port} 接口，被注入到主适配器（Controller）</li>
 * </ul>
 */
@Service
public class OrderService implements SubmitOrderPort {

    private final OrderRepositoryPort repositoryPort;

    public OrderService(OrderRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    @Transactional
    public Long submit(SubmitOrderCommand cmd) {
        List<Order.OrderItem> items = cmd.items().stream()
            .map(i -> new Order.OrderItem(i.productId(), i.productName(), i.quantity(), i.unitPrice()))
            .toList();

        Order order = Order.create(cmd.customerId(), items);
        repositoryPort.save(order);
        return order.id();
    }
}