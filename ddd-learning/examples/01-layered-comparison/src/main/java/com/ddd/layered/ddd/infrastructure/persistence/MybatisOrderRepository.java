package com.ddd.layered.ddd.infrastructure.persistence;

import com.ddd.layered.ddd.domain.model.*;
import com.ddd.layered.ddd.domain.repository.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 订单仓储 MyBatis 实现（从适配器）。
 *
 * <p>实现领域层定义的 {@link OrderRepository} 接口。
 * <p>本类只能被应用服务或领域服务注入，<b>绝不</b>被 Controller 直接注入。
 */
@Repository
public class MybatisOrderRepository implements OrderRepository {

    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderPOAssembler assembler = new OrderPOAssembler();

    public MybatisOrderRepository(OrderMapper orderMapper, OrderItemMapper itemMapper) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
    }

    @Override
    public void save(Order order) {
        OrderPO po = assembler.toPO(order);
        if (po.getId() == null) {
            orderMapper.insert(po);
        } else {
            orderMapper.updateById(po);
        }
        // 实际工程还需处理 order_items 写入（删旧插新）
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        OrderPO po = orderMapper.selectById(id.value());
        if (po == null) {
            return Optional.empty();
        }
        // 简化：实际工程需查询 items
        List<OrderItem> items = List.of();
        return Optional.of(assembler.toDomain(po, items));
    }
}