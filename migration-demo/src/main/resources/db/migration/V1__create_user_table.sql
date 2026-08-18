-- V1：初始版本 - 创建用户表
-- 说明：一个脚本一个主题；DDL 在前；列注释写清楚用途
CREATE TABLE `user` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`   VARCHAR(64)  NOT NULL COMMENT '用户名',
    `nickname`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
    `phone`      VARCHAR(20)  NOT NULL COMMENT '手机号',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-禁用',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_phone` (`phone`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';
