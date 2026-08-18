package com.mall.controller;

import com.mall.common.Result;
import com.mall.dto.CreateOrderDTO;
import com.mall.interceptor.JwtInterceptor.UserContext;
import com.mall.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 订单模块：需要登录（拦截器保护），userId 一律取自 Token，禁止前端传参，防越权。
 */
@Tag(name = "订单模块")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "下单（防超卖）")
    @PostMapping("/create")
    public Result<Map<String, String>> create(@Valid @RequestBody CreateOrderDTO dto) {
        String orderNo = orderService.createOrder(UserContext.userId(), dto);
        return Result.ok(Map.of("orderNo", orderNo));
    }

    @Operation(summary = "支付回调（模拟支付网关调用）")
    @PostMapping("/pay/callback/{orderNo}")
    public Result<Void> payCallback(@PathVariable String orderNo) {
        orderService.payCallback(orderNo);
        return Result.ok();
    }

    @Operation(summary = "订单详情（归属校验）")
    @GetMapping("/detail/{orderNo}")
    public Result<Object> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(UserContext.userId(), orderNo));
    }
}
