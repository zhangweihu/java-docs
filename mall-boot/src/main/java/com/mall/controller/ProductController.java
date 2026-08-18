package com.mall.controller;

import com.mall.common.Result;
import com.mall.entity.Product;
import com.mall.service.ProductService;
import com.mall.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品模块：浏览接口公开（已配置放行），管理接口未做权限细分（演示）。
 */
@Tag(name = "商品模块")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "商品列表（公开）")
    @GetMapping("/list")
    public Result<List<ProductVO>> list() {
        return Result.ok(productService.list());
    }

    @Operation(summary = "商品详情（带缓存）")
    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }

    @Operation(summary = "新增商品（演示）")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody Product product) {
        productService.add(product);
        return Result.ok();
    }
}
