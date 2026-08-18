package com.mall.product.controller;

import com.mall.common.Result;
import com.mall.product.entity.Product;
import com.mall.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品服务接口。
 * /internal/** 为服务间契约实现（对应 mall-common 中 ProductClient 契约，
 * 由提供方实现、消费方依赖抽象 = DIP），网关不放行。
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** 商品列表（公开） */
    @GetMapping("/list")
    public Result<List<Product>> list() {
        return productService.list();
    }

    /** 商品详情（公开） */
    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        return productService.detail(id);
    }

    /** 内部契约实现：单价快照 */
    @GetMapping("/internal/product/{id}/price")
    public Result<BigDecimal> getPrice(@PathVariable("id") Long productId) {
        return productService.getPrice(productId);
    }

    /** 内部契约实现：乐观扣减库存 */
    @PostMapping("/internal/product/deduct")
    public Result<Void> deductStock(@RequestParam Long productId, @RequestParam Integer quantity) {
        return productService.deductStock(productId, quantity);
    }
}
