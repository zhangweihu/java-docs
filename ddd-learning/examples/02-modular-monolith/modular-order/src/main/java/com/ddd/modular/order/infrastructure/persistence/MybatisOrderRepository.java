package com.ddd.modular.order.infrastructure.persistence;

import com.ddd.modular.order.domain.model.Order;
import com.ddd.modular.order.domain.repository.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisOrderRepository implements OrderRepository {

    private final OrderMapper mapper;

    public MybatisOrderRepository(OrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Order order) {
        OrderPO po = toPO(order);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    private OrderPO toPO(Order order) {
        OrderPO po = new OrderPO();
        po.setId(order.id());
        po.setOrderNo(order.orderNo());
        po.setCustomerId(order.customerId());
        po.setStatus(order.status().name());
        po.setCreatedAt(order.createdAt());
        return po;
    }

    private Order toDomain(OrderPO po) {
        return new Order(po.getId(), po.getOrderNo(), po.getCustomerId(),
            List.of(), po.getCreatedAt());
    }
}