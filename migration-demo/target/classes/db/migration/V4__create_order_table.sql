-- V4：创建订单表（演示多表迁移 + 组合索引设计）
CREATE TABLE `order` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`    VARCHAR(32)  NOT NULL COMMENT '订单号',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '下单用户ID',
    `amount`      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '订单金额',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
    `pay_time`    DATETIME     NULL COMMENT '支付时间',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    -- 组合索引：用户维度查询订单（user_id + status + create_time 覆盖常见查询）
    KEY `idx_order_user_status` (`user_id`, `status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单表';
