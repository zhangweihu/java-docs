package com.mall.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单号生成器。
 * 格式：yyyyMMddHHmmss + 4位随机数 + 4位用户ID尾号，保证可读且冲突概率极低。
 * 说明：生产环境建议用雪花算法（Snowflake）生成 64 位长整型订单号，
 * 本项目为了演示可读性使用时间戳方案，配合 t_order.order_no 唯一索引兜底。
 */
public final class OrderNoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private OrderNoGenerator() {
    }

    public static String generate(Long userId) {
        String time = LocalDateTime.now().format(FMT);
        String rand = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        String uid = String.format("%04d", userId % 10000);
        return time + rand + uid;
    }
}
