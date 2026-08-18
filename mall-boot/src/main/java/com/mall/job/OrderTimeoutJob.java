package com.mall.job;

import com.mall.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时关单定时任务。
 * 每 60 秒扫描一次超过 30 分钟未支付的订单并取消。
 * 说明：演示用 Spring 自带的 @Scheduled；生产环境建议：
 *   1. 消息队列延迟消息（RocketMQ/Kafka 定时消息）逐单触发
 *   2. XXL-Job 分布式调度（参考学习文档第 18 章）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final OrderService orderService;

    /** 初始延迟 10 秒，之后每 60 秒执行一次 */
    @Scheduled(initialDelay = 10_000, fixedDelay = 60_000)
    public void closeTimeoutOrders() {
        try {
            orderService.closeExpiredOrders();
            log.info("订单超时关单任务执行完成");
        } catch (Exception e) {
            log.error("订单超时关单任务执行失败", e);
        }
    }
}
