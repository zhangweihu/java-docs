package com.mall.order.controller;

import com.mall.common.Result;
import com.mall.order.entity.Order;
import com.mall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单服务接口。
 * userId 不接收前端传参，从网关注入的请求头 X-User-Id 获取（防越权）。
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 下单（需登录，网关已校验 Token 并注入 X-User-Id） */
    @PostMapping("/create")
    public Result<String> create(@RequestHeader("X-User-Id") Long userId,
                                 @RequestParam Long productId,
                                 @RequestParam Integer quantity) {
        return orderService.createOrder(userId, productId, quantity);
    }

    /** 订单详情 */
    @GetMapping("/detail/{orderNo}")
    public Result<Order> detail(@PathVariable String orderNo) {
        return orderService.detail(orderNo);
    }
}
