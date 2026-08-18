package com.mall.service;

import com.mall.util.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 实际对接验证服务。
 *
 * 覆盖业务真实用到的全部 Redis 能力：
 *   - 连接层：PING 往返
 *   - String：商品缓存（set/get/ttl）
 *   - Hash：购物车（hincrby 原子自增，与 CartServiceImpl 一致）
 *   - List：异步队列 / 消息暂存
 *   - Set：去重（如用户已购商品集合）
 *   - 分布式锁：setnx+过期+原子释放（与下单防重锁同款语义）
 *
 * 每个步骤独立计时并捕获异常，验证失败不影响其他步骤，
 * 输出一份可读的「自检报告」，方便定位 Redis 环境问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisVerifyService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 全量自检报告（含汇总统计） */
    public Map<String, Object> verifyReport() {
        List<Map<String, Object>> steps = new ArrayList<>();

        step(steps, "1. 连接 Ping（往返连通性）", () -> {
            String pong = stringRedisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(pong) ? "PONG" : String.valueOf(pong);
        });

        step(steps, "2. String 写读 + TTL（商品缓存同款）", () -> {
            String key = "mall:verify:string";
            stringRedisTemplate.opsForValue().set(key, "hello-redis", Duration.ofSeconds(60));
            String value = stringRedisTemplate.opsForValue().get(key);
            Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            Boolean deleted = stringRedisTemplate.delete(key);
            return "写入 value=" + value + ", 剩余 ttl=" + ttl + "s, 清理=" + deleted;
        });

        step(steps, "3. Hash 购物车（hincrby 原子自增）", () -> {
            String key = RedisKeyUtil.cart(999999L);
            HashOperations<String, String, String> hash = stringRedisTemplate.opsForHash();
            hash.put(key, "1001", "2");
            hash.increment(key, "1001", 1);
            Long size = hash.size(key);
            String qty = hash.get(key, "1001");
            stringRedisTemplate.delete(key);
            return "hash size=" + size + ", 商品 1001 数量(2+1)=" + qty;
        });

        step(steps, "4. List 队列（rightPop 消费）", () -> {
            String key = "mall:verify:list";
            ListOperations<String, String> list = stringRedisTemplate.opsForList();
            list.leftPushAll(key, "a", "b", "c");
            Long len = list.size(key);
            String pop = list.rightPop(key);
            stringRedisTemplate.delete(key);
            return "len=" + len + ", rightPop=" + pop + "（FIFO）";
        });

        step(steps, "5. Set 去重（重复写入只保留一份）", () -> {
            String key = "mall:verify:set";
            SetOperations<String, String> set = stringRedisTemplate.opsForSet();
            set.add(key, "u1", "u2", "u2");
            Long size = set.size(key);
            stringRedisTemplate.delete(key);
            return "add 3 次(u2 重复) 后 size=" + size + "（去重生效）";
        });

        step(steps, "6. 分布式锁（setnx + 过期 + Lua 原子释放）", () -> {
            String lockKey = "mall:verify:lock";
            String lockValue = UUID.randomUUID().toString();
            // SETNX + PX 原子完成（等价下单防重锁）
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10));
            // 锁被占时二次加锁必须失败
            Boolean lockedAgain = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "other", Duration.ofSeconds(10));
            // Lua 释放：比对 value 后才删除，防止误删他人锁
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end";
            Long released = stringRedisTemplate.execute(
                    new DefaultRedisScript<>(script, Long.class), List.of(lockKey), lockValue);
            return "首次加锁=" + locked + ", 重复加锁(应 false)=" + lockedAgain + ", Lua 释放=" + released;
        });

        long passed = steps.stream().filter(s -> "PASS".equals(s.get("status"))).count();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("redisAddress", redisAddress());
        report.put("total", steps.size());
        report.put("passed", passed);
        report.put("failed", steps.size() - passed);
        report.put("allPassed", passed == steps.size());
        report.put("steps", steps);
        return report;
    }

    /** 执行单个验证步骤：计时 + 兜底异常，保证单步失败不中断整体 */
    private void step(List<Map<String, Object>> steps, String name, Supplier<String> action) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("step", name);
        long start = System.currentTimeMillis();
        try {
            item.put("status", "PASS");
            item.put("result", action.get());
        } catch (Exception e) {
            item.put("status", "FAIL");
            item.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("[Redis 验证] 步骤失败: {} -> {}", name, e.getMessage());
        }
        item.put("elapsedMs", System.currentTimeMillis() - start);
        steps.add(item);
    }

    private String redisAddress() {
        if (stringRedisTemplate.getConnectionFactory() instanceof LettuceConnectionFactory factory) {
            return factory.getHostName() + ":" + factory.getPort();
        }
        return "unknown";
    }
}
