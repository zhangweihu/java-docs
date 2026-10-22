-- V2：为 user 表新增 email 字段并回填存量数据
-- 演示"结构变更 + 数据补齐"放在同一版本（DDL 在前，DML 在后）
ALTER TABLE `user`
    ADD COLUMN `email` VARCHAR(128) NULL COMMENT '邮箱' AFTER `phone`;

-- 存量数据回填：先补数据再加唯一索引，避免大表加索引时反复扫描
UPDATE `user` SET `email` = CONCAT('user_', `id`, '@example.com') WHERE `email` IS NULL;

ALTER TABLE `user`
    ADD UNIQUE KEY `uk_user_email` (`email`);
