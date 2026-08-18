package com.mall.order.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.mall.common.Result;

/**
 * 订单服务 Sentinel 兜底处理器。
 *
 * 规则：blockHandler/fallback 必须是 public static 方法，
 *       参数签名 = 原方法参数 + 末尾追加 BlockException（blockHandler）或 Throwable（fallback）。
 * 优先级：blockHandler（被限流/降级） > fallback（其他异常）。
 */
public final class OrderSentinelBlockHandler {

    /** 下单被限流时返回（对应 OrderServiceImpl.createOrder 的 blockHandler） */
    public static Result<String> createOrderBlock(Long userId, Long productId, Integer quantity, BlockException e) {
        return Result.fail(429, "下单请求过于频繁，已被 Sentinel 限流，请稍后再试");
    }

    /** 下单发生其他异常时返回（对应 OrderServiceImpl.createOrder 的 fallback） */
    public static Result<String> createOrderFallback(Long userId, Long productId, Integer quantity, Throwable t) {
        return Result.fail(500, "下单服务繁忙，已触发 Sentinel fallback 兜底");
    }

    private OrderSentinelBlockHandler() {
    }
}
