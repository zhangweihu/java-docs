package com.mall.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 用户服务启动类。
 * @ComponentScan("com.mall") 扫描 mall-common 中的全局异常处理器/配置，保证包外生效。
 */
@EnableDiscoveryClient // 注册到 Nacos
@SpringBootApplication
@ComponentScan("com.mall")
@MapperScan("com.mall.user.mapper")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
