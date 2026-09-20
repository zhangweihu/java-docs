-- 模块化单体四模块建表（精简版）
-- 兼容 MySQL 8.0 / H2 MySQL 模式

-- 用户表
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL,
    email       VARCHAR(128) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
);

-- 订单表
DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(64)  NOT NULL,
    customer_id  BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    total_amount DECIMAL(18, 2) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_customer_id (customer_id)
);

-- 库存表
DROP TABLE IF EXISTS t_inventory;
CREATE TABLE t_inventory (
    product_id   BIGINT       NOT NULL,
    available    INT          NOT NULL,
    reserved     INT          NOT NULL DEFAULT 0,
    sold         INT          NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id),
    CHECK (available >= 0 AND reserved >= 0 AND sold >= 0)
);

-- 账户表
DROP TABLE IF EXISTS t_account;
CREATE TABLE t_account (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    balance      DECIMAL(18, 2) NOT NULL DEFAULT 0,
    frozen       DECIMAL(18, 2) NOT NULL DEFAULT 0,
    currency     VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    CHECK (balance >= 0 AND frozen >= 0)
);