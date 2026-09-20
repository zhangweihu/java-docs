package com.ddd.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Saga 集成示例启动类。
 *
 * <p>三个上下文（订单/库存/支付）单进程运行，通过 Saga 协调 + Outbox 模拟分布式事务。
 * <p>{@code @EnableScheduling} 启用 OutboxScheduler 定时任务。
 */
@SpringBootApplication
@EnableScheduling
public class DddSagaApplication {

    public static void main(String[] args) {
        SpringApplication.run(DddSagaApplication.class, args);
    }
}