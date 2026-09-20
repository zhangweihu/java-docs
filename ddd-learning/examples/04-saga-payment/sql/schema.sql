-- Saga 集成示例：订单/库存/支付/Outbox 表
DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(64)  NOT NULL,
    customer_id  BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    amount       DECIMAL(18, 2) NOT NULL,
    saga_id      VARCHAR(64),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_saga_id (saga_id)
);

DROP TABLE IF EXISTS t_inventory;
CREATE TABLE t_inventory (
    product_id   BIGINT       NOT NULL,
    available    INT          NOT NULL,
    reserved     INT          NOT NULL DEFAULT 0,
    sold         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id),
    CHECK (available >= 0 AND reserved >= 0 AND sold >= 0)
);

DROP TABLE IF EXISTS t_account;
CREATE TABLE t_account (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    balance      DECIMAL(18, 2) NOT NULL DEFAULT 0,
    frozen       DECIMAL(18, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    CHECK (balance >= 0 AND frozen >= 0)
);

DROP TABLE IF EXISTS outbox_event;
CREATE TABLE outbox_event (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    event_type    VARCHAR(64)  NOT NULL,
    aggregate_id  VARCHAR(64)  NOT NULL,
    payload       TEXT         NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    retry_count   INT          NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_status_next_retry (status, next_retry_at)
);

DROP TABLE IF EXISTS saga_log;
CREATE TABLE saga_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    saga_id      VARCHAR(64)  NOT NULL,
    saga_type    VARCHAR(64)  NOT NULL,
    step_name    VARCHAR(64)  NOT NULL,
    step_status  VARCHAR(16)  NOT NULL,
    error_msg    TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_saga_id (saga_id)
);