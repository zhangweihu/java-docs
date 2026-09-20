package com.ddd.layered.clean.entities;

/**
 * 整洁架构风格的订单仓储接口（领域层定义）。
 *
 * <p>与 DDD 四层的 {@code OrderRepository} 完全等价。
 */
public interface OrderRepository {
    void save(Order order);
    Order findById(Long id);
}