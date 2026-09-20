package com.ddd.layered.hexagonal.ports.out;

import com.ddd.layered.hexagonal.domain.Order;

import java.util.Optional;

/**
 * 出口端口（Outbound Port / Driven Port）。
 *
 * <p>业务核心"需要"的外部能力。任何适配器（MySQL、Redis、Mock、外部 RPC）
 * 都可以实现此端口。业务核心不感知实现细节。
 */
public interface OrderRepositoryPort {
    void save(Order order);
    Optional<Order> findById(Long id);
}