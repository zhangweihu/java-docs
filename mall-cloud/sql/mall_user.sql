-- 用户服务独立数据库：mall_user（独立数据库原则：服务间不共享库表）
CREATE DATABASE IF NOT EXISTS mall_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE mall_user;

CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username    VARCHAR(32)     NOT NULL,
    password    VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密文',
    nickname    VARCHAR(32)     DEFAULT NULL,
    phone       VARCHAR(20)     DEFAULT NULL,
    status      TINYINT         NOT NULL DEFAULT 0,
    deleted     TINYINT         NOT NULL DEFAULT 0,
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB COMMENT ='用户表';

-- Seata AT 模式必需的 undo_log 表（每个参与分布式事务的库都要建）
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
