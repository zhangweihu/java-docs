package com.mall.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 订单服务启动类。
 * @EnableFeignClients("com.mall.common.feign") 启用公共模块中定义的 Feign 契约，
 * 契约由消费方启用（DIP：提供方无需感知谁在调用）。
 */
@EnableDiscoveryClient
@EnableFeignClients("com.mall.common.feign")
@SpringBootApplication
@ComponentScan("com.mall")
@MapperScan("com.mall.order.mapper")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
