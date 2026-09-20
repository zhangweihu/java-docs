package com.ddd.ecommerce.order.infrastructure.persistence;

import com.ddd.ecommerce.order.domain.model.*;

import java.util.List;

/** PO ↔ 领域对象 转换器。 */
public class OrderPOAssembler {

    public OrderPO toPO(Order order) {
        OrderPO po = new OrderPO();
        po.setId(order.id().value());
        po.setOrderNo(order.orderNo());
        po.setCustomerId(order.customerId().value());
        po.setStatus(order.status().name());
        Money total = order.total();
        po.setTotalAmount(total.amount());
        po.setCurrency(total.currency().name());
        po.setCreatedAt(order.createdAt());
        po.setUpdatedAt(order.updatedAt());
        return po;
    }

    public Order toDomain(OrderPO po, List<OrderItem> items) {
        return new Order(
            new OrderId(po.getId()),
            po.getOrderNo(),
            new CustomerId(po.getCustomerId()),
            items,
            po.getCreatedAt()
        );
    }
}