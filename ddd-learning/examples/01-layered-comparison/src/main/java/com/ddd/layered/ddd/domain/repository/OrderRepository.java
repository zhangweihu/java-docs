package com.ddd.layered.ddd.domain.repository;

import com.ddd.layered.ddd.domain.model.Order;
import com.ddd.layered.ddd.domain.model.OrderId;

import java.util.Optional;

/**
 * 订单仓储接口（领域层定义）。
 *
 * <p><b>依赖倒置</b>：接口在领域层，实现在基础设施层。领域层不知道 MyBatis 存在。
 */
public interface OrderRepository {

    /** 保存新订单或更新已有订单。 */
    void save(Order order);

    /** 根据 ID 查询。 */
    Optional<Order> findById(OrderId id);
}