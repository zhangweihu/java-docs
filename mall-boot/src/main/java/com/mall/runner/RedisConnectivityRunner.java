package com.mall.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 启动时 Redis 连通性自检。
 *
 * 设计取舍：
 *   - 仅打日志、不阻断启动 —— 若 Redis 未就绪，业务接口的缓存/锁会降级，
 *     服务本身不应因此挂掉（可用性优先）；
 *   - 后续仍可通过 GET /api/redis/verify 做完整验证。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisConnectivityRunner implements ApplicationRunner {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String pong = stringRedisTemplate.getConnectionFactory().getConnection().ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("ping 返回异常: " + pong);
            }
            stringRedisTemplate.opsForValue()
                    .set("mall:startup:probe", String.valueOf(System.currentTimeMillis()), Duration.ofSeconds(30));
            log.info("[Redis 自检] PASS —— Ping 返回 PONG，探针 key 已写入（30s 自动过期）");
        } catch (Exception e) {
            log.warn("[Redis 自检] FAIL —— 无法连接 Redis: {}. "
                    + "请确认已执行 docker compose -f docker/docker-compose.yml up -d redis；"
                    + "缓存/分布式锁功能将降级，业务主流程不受影响", e.getMessage());
        }
    }
}
