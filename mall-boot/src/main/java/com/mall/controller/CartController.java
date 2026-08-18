package com.mall.controller;

import com.mall.common.Result;
import com.mall.interceptor.JwtInterceptor.UserContext;
import com.mall.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 购物车模块：Redis Hash 存储，需要登录。
 */
@Tag(name = "购物车模块")
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "加入购物车")
    @PostMapping("/add/{productId}")
    public Result<Void> add(@PathVariable Long productId, @RequestParam Integer quantity) {
        cartService.add(UserContext.userId(), productId, quantity);
        return Result.ok();
    }

    @Operation(summary = "查看购物车")
    @GetMapping("/list")
    public Result<Map<Object, Object>> list() {
        return Result.ok(cartService.list(UserContext.userId()));
    }

    @Operation(summary = "修改数量（<=0 则删除）")
    @PutMapping("/update/{productId}")
    public Result<Void> update(@PathVariable Long productId, @RequestParam Integer quantity) {
        cartService.update(UserContext.userId(), productId, quantity);
        return Result.ok();
    }

    @Operation(summary = "删除某项")
    @DeleteMapping("/remove/{productId}")
    public Result<Void> remove(@PathVariable Long productId) {
        cartService.remove(UserContext.userId(), productId);
        return Result.ok();
    }
}
