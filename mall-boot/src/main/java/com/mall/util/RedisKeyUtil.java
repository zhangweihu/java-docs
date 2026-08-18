package com.mall.util;

/**
 * Redis Key 规范：统一以业务模块前缀 + ":" 分隔，便于监控与排查。
 * 示例：mall:product:detail:1001、mall:lock:order:1001
 */
public final class RedisKeyUtil {

    /** 商品详情缓存 */
    public static String productDetail(Long productId) {
        return "mall:product:detail:" + productId;
    }

    /** 购物车：Hash 结构，field 为商品ID */
    public static String cart(Long userId) {
        return "mall:cart:" + userId;
    }

    /** 下单防重锁（分布式锁） */
    public static String orderLock(Long userId, Long productId) {
        return "mall:lock:order:" + userId + ":" + productId;
    }

    /** 登录用户 Token 黑名单（登出后写入，过期时间=Token剩余有效期） */
    public static String tokenBlacklist(String token) {
        return "mall:token:blacklist:" + token;
    }

    private RedisKeyUtil() {
    }
}
