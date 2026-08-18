-- V3：初始化演示数据（INSERT 前先防御性清理，保证幂等语义）
DELETE FROM `user` WHERE `username` IN ('zhangsan', 'lisi');

INSERT INTO `user` (`username`, `nickname`, `phone`, `email`, `status`) VALUES
('zhangsan', '张三', '13800000001', 'zhangsan@example.com', 1),
('lisi',     '李四', '13800000002', 'lisi@example.com',     1);
