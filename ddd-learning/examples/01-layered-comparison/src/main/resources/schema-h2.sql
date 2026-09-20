-- H2 内存数据库建表（H2 MySQL 模式）
DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(64)  NOT NULL,
    customer_id  BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    total_amount DECIMAL(18, 2) NOT NULL,
    currency     VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no)
);

DROP TABLE IF EXISTS t_order_item;
CREATE TABLE t_order_item (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    product_id  BIGINT        NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    quantity    INT           NOT NULL,
    unit_price  DECIMAL(18, 2) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_order_id (order_id)
);