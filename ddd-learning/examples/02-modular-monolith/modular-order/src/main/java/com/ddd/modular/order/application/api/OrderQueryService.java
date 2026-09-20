package com.ddd.modular.order.application.api;

import com.ddd.modular.common.dto.PageResult;

import java.util.Optional;

/**
 * 订单查询 API（对外暴露）。
 *
 * <p>其他模块只能注入此接口。
 */
public interface OrderQueryService {

    Optional<OrderView> findById(Long orderId);

    PageResult<OrderView> search(SearchOrderQuery query);

    record OrderView(Long id, String orderNo, Long customerId, String status) {}

    record SearchOrderQuery(Long customerId, String status, int page, int size) {}
}