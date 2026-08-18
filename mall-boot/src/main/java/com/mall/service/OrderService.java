package com.mall.service;

import com.mall.dto.CreateOrderDTO;

/**
 * 订单服务接口。
 */
public interface OrderService {

    /** 下单：事务 + 分布式锁 + 乐观扣减，三重保障防超卖 */
    String createOrder(Long userId, CreateOrderDTO dto);

    /** 支付回调（模拟支付网关）：按状态机流转，幂等处理重复回调 */
    void payCallback(String orderNo);

    /** 订单详情（归属校验：只能查自己的订单） */
    Object detail(Long userId, String orderNo);

    /** 超时未支付关单（定时任务调用） */
    void closeExpiredOrders();
}
