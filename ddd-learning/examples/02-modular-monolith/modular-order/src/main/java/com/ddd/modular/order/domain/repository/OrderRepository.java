package com.ddd.modular.order.domain.repository;

import com.ddd.modular.order.domain.model.Order;

import java.util.Optional;

/** 订单仓储接口。 */
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(Long id);
}