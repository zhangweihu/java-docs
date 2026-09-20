package com.ddd.layered;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DDD × 分层架构对比工程主类。
 *
 * <p>支持通过 Profile 切换演示不同分层风格：
 * <ul>
 *   <li>默认 ddd —— DDD 四层风格（{@code /ddd/orders}）</li>
 *   <li>{@code clean} —— 整洁架构风格（{@code /clean/orders}）</li>
 *   <li>{@code hex} —— 六边形架构风格（{@code /hex/orders}）</li>
 * </ul>
 *
 * <p>本工程同时启用三种风格的 Controller，可在同一进程内对照查看。
 */
@SpringBootApplication
public class DddLayeredApplication {

    public static void main(String[] args) {
        SpringApplication.run(DddLayeredApplication.class, args);
    }
}