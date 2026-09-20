package com.ddd.ecommerce.order.domain.repository;

import com.ddd.ecommerce.order.domain.model.Order;
import com.ddd.ecommerce.order.domain.model.OrderId;

import java.util.Optional;

/** 订单仓储接口（领域层定义）。 */
public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(OrderId id);
}