package com.ddd.layered.clean.application;

import com.ddd.layered.clean.application.command.SubmitOrderCommand;
import com.ddd.layered.clean.entities.Order;
import com.ddd.layered.clean.entities.OrderItem;
import com.ddd.layered.clean.entities.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 整洁架构风格的 UseCase 实现。
 *
 * <p>注意命名：实现类叫 {@code SubmitOrderUseCaseImpl}（Clean 风格）或
 * {@code DefaultSubmitOrderUseCase}（变体）。区别于 DDD 风格叫 {@code *AppService}。
 */
@Service
public class SubmitOrderUseCaseImpl implements SubmitOrderUseCase {

    private final OrderRepository orderRepository;

    public SubmitOrderUseCaseImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public Long execute(SubmitOrderCommand cmd) {
        // 1. 转换为领域实体（Command → Entity）
        List<OrderItem> items = cmd.items().stream()
            .map(i -> new OrderItem(i.productId(), i.productName(), i.quantity(), i.unitPrice()))
            .toList();

        // 2. 创建订单（充血实体做不变量校验）
        Order order = Order.create(cmd.customerId(), items);

        // 3. 持久化
        orderRepository.save(order);

        // 4. 返回领域标识
        return order.id();
    }
}