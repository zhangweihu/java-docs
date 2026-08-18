package com.mall.controller;

import com.mall.common.Result;
import com.mall.service.RedisVerifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Redis 对接验证接口。
 *
 * 用途：验证业务用到的 Redis 能力（String/Hash/List/Set/分布式锁）是否真正可用。
 * 在部署（本地/容器/K8s）后先调一次本接口，快速确认 Redis 环境无误再继续联调。
 *
 * 调用：GET /api/redis/verify
 */
@RestController
@RequestMapping("/api/redis")
@RequiredArgsConstructor
public class RedisVerifyController {

    private final RedisVerifyService redisVerifyService;

    @GetMapping("/verify")
    public Result<Map<String, Object>> verify() {
        return Result.ok(redisVerifyService.verifyReport());
    }
}
