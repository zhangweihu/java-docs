-- =============================================================
-- Mall 商城单体项目数据库初始化脚本
-- 使用：mysql -uroot -p < mall.sql
-- 说明：t_user / t_product 含逻辑删除字段 deleted；订单号唯一索引保证幂等
-- =============================================================

CREATE DATABASE IF NOT EXISTS mall DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE mall;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)     NOT NULL COMMENT '用户名',
    password    VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密文',
    nickname    VARCHAR(32)     DEFAULT NULL COMMENT '昵称',
    phone       VARCHAR(20)     DEFAULT NULL COMMENT '手机号',
    status      TINYINT         NOT NULL DEFAULT 0 COMMENT '0-正常 1-禁用',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-否 1-是',
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)          -- 防并发重复注册
) ENGINE = InnoDB COMMENT ='用户表';

-- 商品表
CREATE TABLE IF NOT EXISTS t_product (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100)    NOT NULL COMMENT '商品名称',
    description VARCHAR(500)    DEFAULT NULL COMMENT '商品描述',
    price       DECIMAL(10, 2)  NOT NULL COMMENT '单价（元）',
    stock       INT             NOT NULL DEFAULT 0 COMMENT '库存',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1-上架 0-下架',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT ='商品表';

-- 订单表
CREATE TABLE IF NOT EXISTS t_order (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no    VARCHAR(32)     NOT NULL COMMENT '订单号（业务主键）',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    product_id  BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    price       DECIMAL(10, 2)  NOT NULL COMMENT '单价快照',
    quantity    INT             NOT NULL COMMENT '数量',
    total_amount DECIMAL(12, 2) NOT NULL COMMENT '总金额',
    status      TINYINT         NOT NULL DEFAULT 0 COMMENT '0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
    pay_time    DATETIME        DEFAULT NULL COMMENT '支付时间',
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),          -- 幂等兜底
    KEY idx_user (user_id),
    KEY idx_status_create (status, create_time) -- 超时关单扫描索引
) ENGINE = InnoDB COMMENT ='订单表';

-- 演示数据
INSERT INTO t_product (name, description, price, stock, status) VALUES
('Java 编程思想', '经典 Java 入门书', 108.00, 100, 1),
('Spring 实战',    'Spring 框架实战指南', 89.00, 200, 1),
('Redis 深度笔记',  '缓存与高可用原理', 79.00, 150, 1);
