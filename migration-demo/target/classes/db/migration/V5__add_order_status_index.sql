-- V5：为订单状态单独加索引（运营后台按 status 全表筛选场景）
-- 注意：真实大表（千万级）加索引请使用影子表 / pt-osc / gh-ost 在线变更，禁止直接 ALTER
ALTER TABLE `order`
    ADD KEY `idx_order_status` (`status`);

-- 演示数据清理：V3 是"初始化"版本，这里在 V4 建表后补一条订单数据
INSERT INTO `order` (`order_no`, `user_id`, `amount`, `status`, `pay_time`) VALUES
('ORD20260101001', 1, 199.90, 1, NOW());
