-- 订单服务独立数据库：mall_order
CREATE DATABASE IF NOT EXISTS mall_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE mall_order;

CREATE TABLE IF NOT EXISTS t_order (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(32)     NOT NULL,
    user_id      BIGINT UNSIGNED NOT NULL,
    product_id   BIGINT UNSIGNED NOT NULL,
    price        DECIMAL(10, 2)  NOT NULL COMMENT '单价快照',
    quantity     INT             NOT NULL,
    total_amount DECIMAL(12, 2)  NOT NULL,
    status       TINYINT         NOT NULL DEFAULT 0 COMMENT '0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
    pay_time     DATETIME        DEFAULT NULL,
    create_time  DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT ='订单表';

-- Seata AT 模式必需的 undo_log 表
CREATE TABLE IF NOT EXISTS undo_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB     NOT NULL,
    log_status    INT          NOT NULL,
    log_created   DATETIME     NOT NULL,
    log_modified  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_undo_log (xid, branch_id)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COMMENT ='Seata 回滚日志表';
