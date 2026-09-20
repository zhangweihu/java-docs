package com.ddd.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 订单领域 Spring Boot 启动类。
 *
 * <p>本工程聚焦"订单上下文的完整 DDD 实战"——聚合充血、状态机、领域事件、防腐层。
 * <p>跨上下文（库存/支付）通过端口（{@code application.api}）调用，实际工程为同进程或远程 Feign。
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.ddd.ecommerce.order")
public class DddEcommerceOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(DddEcommerceOrderApplication.class, args);
    }
}