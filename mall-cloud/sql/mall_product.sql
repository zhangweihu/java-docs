-- 商品服务独立数据库：mall_product
CREATE DATABASE IF NOT EXISTS mall_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE mall_product;

CREATE TABLE IF NOT EXISTS t_product (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(500)    DEFAULT NULL,
    price       DECIMAL(10, 2)  NOT NULL,
    stock       INT             NOT NULL DEFAULT 0,
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1-上架 0-下架',
    deleted     TINYINT         NOT NULL DEFAULT 0,
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT ='商品表';

INSERT INTO t_product (name, description, price, stock, status) VALUES
('Java 编程思想', '经典 Java 入门书', 108.00, 100, 1),
('Spring 实战',    'Spring 框架实战指南', 89.00, 200, 1),
('微服务设计',     '微服务架构设计', 129.00, 50, 1);

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
