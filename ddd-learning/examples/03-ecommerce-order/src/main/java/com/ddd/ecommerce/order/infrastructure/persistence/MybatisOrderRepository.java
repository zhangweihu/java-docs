package com.ddd.ecommerce.order.infrastructure.persistence;

import com.ddd.ecommerce.order.domain.model.*;
import com.ddd.ecommerce.order.domain.repository.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 订单仓储 MyBatis 实现（从适配器）。 */
@Repository
public class MybatisOrderRepository implements OrderRepository {

    private final OrderMapper mapper;
    private final OrderPOAssembler assembler = new OrderPOAssembler();

    public MybatisOrderRepository(OrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Order order) {
        OrderPO po = assembler.toPO(order);
        if (po.getId() == null) {
            // 新订单：实际工程需回填 id 到 order
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        OrderPO po = mapper.selectById(id.value());
        if (po == null) return Optional.empty();
        // 简化：实际工程需查询 items 表
        List<OrderItem> items = List.of();
        return Optional.of(assembler.toDomain(po, items));
    }
}