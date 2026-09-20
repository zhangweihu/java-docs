package com.ddd.modular.order.application.api;

import com.ddd.modular.common.dto.PageResult;
import com.ddd.modular.order.infrastructure.persistence.OrderMapper;
import com.ddd.modular.order.infrastructure.persistence.OrderPO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** 订单查询实现（读侧 CQRS：直查 MyBatis，绕过聚合根）。 */
@Service
public class OrderQueryServiceImpl implements OrderQueryService {

    private final OrderMapper mapper;

    public OrderQueryServiceImpl(OrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<OrderView> findById(Long orderId) {
        return Optional.ofNullable(mapper.selectById(orderId)).map(this::toView);
    }

    @Override
    public PageResult<OrderView> search(SearchOrderQuery query) {
        // 简化：实际需用 LambdaQueryWrapper 构造条件
        List<OrderPO> pos = mapper.selectList(null);
        List<OrderView> views = pos.stream().map(this::toView).toList();
        return PageResult.of(views, query.page(), query.size(), views.size());
    }

    private OrderView toView(OrderPO po) {
        return new OrderView(po.getId(), po.getOrderNo(), po.getCustomerId(), po.getStatus());
    }
}