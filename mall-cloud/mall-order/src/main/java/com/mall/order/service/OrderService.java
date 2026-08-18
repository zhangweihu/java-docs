package com.mall.order.service;

import com.mall.common.Result;
import com.mall.order.entity.Order;

/**
 * 订单服务接口。
 */
public interface OrderService {

    /**
     * 下单：@GlobalTransactional 开启 Seata 全局事务。
     * 流程：查单价(Feign) -> 扣库存(Feign, 商品库) -> 建订单(订单库)，
     * 任一步失败则商品库与订单库整体回滚，保证跨库一致。
     */
    Result<String> createOrder(Long userId, Long productId, Integer quantity);

    /** 订单详情 */
    Result<Order> detail(String orderNo);
}
