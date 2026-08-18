package com.mall.common.feign;

import com.mall.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 商品服务 Feign 契约（定义在公共模块 = 依赖倒置 DIP 落地）。
 *
 * 设计要点：
 *   - 契约（接口）属于"消费方"，由提供方（mall-product）实现内部接口，
 *     消费方（mall-order）只依赖抽象，不依赖具体实现类
 *   - 内部接口路径统一用 /internal/**，网关层禁止放行，防外部直接调用
 *   - name 与提供方 spring.application.name 保持一致，经 Nacos 服务发现路由
 */
@FeignClient(name = "mall-product", contextId = "productClient", path = "/internal/product")
public interface ProductClient {

    /** 查询商品单价快照（供订单服务下单时取价） */
    @GetMapping("/{id}/price")
    Result<BigDecimal> getPrice(@PathVariable("id") Long productId);

    /** 乐观扣减库存：失败返回 Result.code=4003（库存不足） */
    @PostMapping("/deduct")
    Result<Void> deductStock(@RequestParam("productId") Long productId,
                             @RequestParam("quantity") Integer quantity);
}
