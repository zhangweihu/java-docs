package com.mall;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 实际对接集成测试（连真实 Redis，不是 mock）。
 *
 * 前置条件：本地 Redis 已启动
 *   docker compose -f docker/docker-compose.yml up -d redis
 *
 * 运行：mvn test -Dtest=RedisIntegrationTest
 *
 * 测试内容与 RedisVerifyService 的自检步骤一一对应，
 * 单测断言保证「Redis 能力真实可用」而不只是连通。
 */
@Slf4j
@SpringBootTest
class RedisIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void ping_shouldReturnPong() {
        String pong = stringRedisTemplate.getConnectionFactory().getConnection().ping();
        assertEquals("PONG", pong.toUpperCase());
    }

    @Test
    void stringSetGetTtl_shouldWork() {
        String key = "mall:test:string:" + UUID.randomUUID();
        stringRedisTemplate.opsForValue().set(key, "value", Duration.ofSeconds(60));
        assertEquals("value", stringRedisTemplate.opsForValue().get(key));
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 60, "ttl 应在 (0, 60] 秒之间, 实际=" + ttl);
        stringRedisTemplate.delete(key);
        assertNull(stringRedisTemplate.opsForValue().get(key));
    }

    @Test
    void hashCart_shouldIncrementAtomically() {
        String key = "mall:test:cart:" + UUID.randomUUID();
        stringRedisTemplate.opsForHash().put(key, "1001", "2");
        stringRedisTemplate.opsForHash().increment(key, "1001", 1);
        assertEquals("3", stringRedisTemplate.opsForHash().get(key, "1001"));
        assertEquals(1L, stringRedisTemplate.opsForHash().size(key));
        stringRedisTemplate.delete(key);
    }

    @Test
    void list_shouldWorkAsQueue() {
        String key = "mall:test:list:" + UUID.randomUUID();
        stringRedisTemplate.opsForList().leftPushAll(key, "a", "b", "c");
        assertEquals(3L, stringRedisTemplate.opsForList().size(key));
        assertEquals("a", stringRedisTemplate.opsForList().rightPop(key)); // FIFO
        stringRedisTemplate.delete(key);
    }

    @Test
    void set_shouldDeduplicate() {
        String key = "mall:test:set:" + UUID.randomUUID();
        stringRedisTemplate.opsForSet().add(key, "u1", "u2", "u2");
        assertEquals(2L, stringRedisTemplate.opsForSet().size(key), "重复写入应被去重");
        stringRedisTemplate.delete(key);
    }

    @Test
    void distributedLock_shouldBeExclusiveAndReleasable() {
        String lockKey = "mall:test:lock:" + UUID.randomUUID();
        String value = UUID.randomUUID().toString();

        // 第一次加锁成功
        assertTrue(stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, value, Duration.ofSeconds(10)));
        // 锁被占用时二次加锁失败
        assertFalse(stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "other", Duration.ofSeconds(10)));
        // 错误 value 无法释放（防误删他人锁）
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) else return 0 end";
        Long wrongRelease = stringRedisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class),
                List.of(lockKey), "wrong-value");
        assertEquals(0L, wrongRelease);
        // 正确 value 释放成功
        Long release = stringRedisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class),
                List.of(lockKey), value);
        assertEquals(1L, release);
        // 释放后可再次加锁
        assertTrue(stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, value, Duration.ofSeconds(10)));
        stringRedisTemplate.delete(lockKey);
    }
}
