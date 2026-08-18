package com.mall.user.controller;

import com.mall.common.Result;
import com.mall.user.entity.User;
import com.mall.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户服务接口。
 * 注意 /internal/** 路径：仅允许服务间 Feign 调用，网关已配置不放行，
 * 防止内部接口被外部直接访问（防横向越权）。
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 注册（网关白名单） */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody Map<String, String> body) {
        return userService.register(body.get("username"), body.get("password"), body.get("phone"));
    }

    /** 登录（网关白名单），返回 JWT */
    @PostMapping("/login")
    public Result<String> login(@RequestBody Map<String, String> body) {
        return userService.login(body.get("username"), body.get("password"));
    }

    /** 内部接口：查询昵称（Feign 契约 UserClient 对应实现） */
    @GetMapping("/internal/user/{id}/nickname")
    public Result<String> getNickname(@PathVariable("id") Long userId) {
        return userService.getNickname(userId);
    }

    /** 内部接口：查询用户信息 */
    @GetMapping("/internal/user/{id}")
    public Result<User> getById(@PathVariable("id") Long userId) {
        return userService.getById(userId);
    }
}
