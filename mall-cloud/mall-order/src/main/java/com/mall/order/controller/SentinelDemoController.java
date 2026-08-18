package com.mall.order.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.mall.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sentinel 限流演示接口。
 *
 * 快速体验：连续刷新 GET /order/sentinel/demo（QPS 限制 2，见 SentinelFlowRuleConfig），
 * 前几次返回正常，随后返回 429 blockHandler 兜底。
 * 也可用压测：ab -n 100 -c 10 http://localhost:8083/order/sentinel/demo
 */
@RestController
@RequestMapping("/order/sentinel")
public class SentinelDemoController {

    @GetMapping("/demo")
    @SentinelResource(value = "sentinel:demo",
            blockHandler = "demoBlock",
            fallback = "demoFallback")
    public Result<String> demo() {
        return Result.ok("Sentinel 演示：请求正常放行");
    }

    /** 被限流时执行（同包同类方法，签名 = 原方法 + BlockException） */
    public Result<String> demoBlock(BlockException e) {
        return Result.fail(429, "触发 Sentinel 流控（blockHandler）：请求过于频繁");
    }

    /** 其他异常时执行 */
    public Result<String> demoFallback(Throwable t) {
        return Result.fail(500, "Sentinel 演示接口异常（fallback）");
    }
}
