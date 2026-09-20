package com.ddd.modular;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 模块化单体启动类。
 *
 * <p>扫描 {@code com.ddd.modular} 包下所有 Spring 组件。
 * <p>本类所在的 {@code modular-app} 模块是唯一聚合入口——
 * 业务模块不应反向依赖本模块（由 ArchUnit 守护）。
 */
@SpringBootApplication(scanBasePackages = "com.ddd.modular")
public class ModularApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModularApplication.class, args);
    }
}