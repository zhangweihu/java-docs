package com.ddd.layered.ddd.infrastructure.persistence;

import com.ddd.layered.ddd.domain.model.*;

import java.util.UUID;

/**
 * 订单 PO ↔ 领域对象 转换器。
 *
 * <p>所有 PO 与领域对象互转都必须经过此 Assembler，
 * 避免基础设施层的字段直接泄漏到领域层。
 */
public class OrderPOAssembler {

    public OrderPO toPO(Order order) {
        OrderPO po = new OrderPO();
        po.setId(order.id().value());
        po.setOrderNo(generateOrderNo());
        po.setCustomerId(order.customerId().value());
        po.setStatus(order.status().name());
        Money total = order.total();
        po.setTotalAmount(total.amount());
        po.setCurrency(total.currency().name());
        po.setCreatedAt(order.createdAt());
        po.setDeleted(0);
        return po;
    }

    public Order toDomain(OrderPO po, java.util.List<OrderItem> items) {
        return new Order(
            new OrderId(po.getId()),
            new CustomerId(po.getCustomerId()),
            items,
            po.getCreatedAt()
        );
    }

    private String generateOrderNo() {
        return "O" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}