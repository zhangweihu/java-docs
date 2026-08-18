package com.migration.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 数据迁移实操项目启动类（第 38 章配套）。
 *
 * 启动流程：
 *  1. Spring Boot 初始化数据源；
 *  2. Flyway 自动执行 classpath:db/migration 下未执行的迁移脚本（见应用启动日志）；
 *  3. 本类的 CommandLineRunner 打印迁移结果，便于直观验证。
 */
@SpringBootApplication
public class MigrationDemoApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationDemoApplication.class);

    private final JdbcTemplate jdbcTemplate;

    public MigrationDemoApplication(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(MigrationDemoApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("========== 迁移结果验证 ==========");

        // 1. 查看 Flyway 迁移历史表：确认 5 个版本全部执行成功
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank");
        log.info("Flyway 迁移历史：");
        history.forEach(row -> log.info("  rank={}, version={}, description={}, success={}",
                row.get("installed_rank"), row.get("version"), row.get("description"), row.get("success")));

        // 2. 查询用户表数据（V3 初始化）
        Long userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `user`", Long.class);
        log.info("user 表数据条数：{}（预期 2）", userCount);

        // 3. 查询订单表数据（V4/V5 建表 + 数据）
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                "SELECT order_no, user_id, amount, status FROM `order`");
        log.info("order 表数据：{}", orders);

        log.info("========== 迁移验证完成 ==========");
    }
}
