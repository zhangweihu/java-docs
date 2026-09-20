package com.ddd.layered.hexagonal.adapters.out.persistence;

import com.ddd.layered.hexagonal.domain.Order;
import com.ddd.layered.hexagonal.ports.out.OrderRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 出口适配器（Outbound Adapter / Driven Adapter）。
 *
 * <p>本类实现 {@link OrderRepositoryPort} 出口端口，提供"内存 Map"实现。
 * 实际工程可换为 MyBatis 实现（{@code MybatisOrderRepositoryAdapter}），
 * 通过 Spring Bean 切换依赖绑定即可——业务核心完全无感。
 */
@Repository
public class HexOrderRepositoryAdapter implements OrderRepositoryPort {

    private final Map<Long, Order> store = new HashMap<>();

    @Override
    public void save(Order order) {
        store.put(order.id(), order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }
}