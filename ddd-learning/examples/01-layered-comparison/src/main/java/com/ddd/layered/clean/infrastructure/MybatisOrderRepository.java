package com.ddd.layered.clean.infrastructure;

import com.ddd.layered.clean.entities.Order;
import com.ddd.layered.clean.entities.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * 整洁架构风格的仓储实现（简化版，使用内存 Map 演示依赖倒置）。
 *
 * <p>实际工程中此处会用 MyBatis / JPA 实现，本类保持简洁以便对照分层差异。
 */
@Repository
public class MybatisOrderRepository implements OrderRepository {

    private final Map<Long, Order> store = new HashMap<>();

    @Override
    public void save(Order order) {
        store.put(order.id(), order);
    }

    @Override
    public Order findById(Long id) {
        Order order = store.get(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在：" + id);
        }
        return order;
    }
}