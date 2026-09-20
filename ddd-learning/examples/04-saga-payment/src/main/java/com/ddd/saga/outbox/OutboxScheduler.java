package com.ddd.saga.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox Scheduler（后台定时扫描 + 投递）。
 *
 * <p>每 5 秒扫描 PENDING 状态事件，投递到 MQ，标记为 DISPATCHED。
 * <p>失败时：递增 retry_count + 指数退避 next_retry_at。
 * <p>超过最大重试次数：标记为 FAILED（进死信队列）。
 */
@Component
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);
    private static final int MAX_RETRY = 10;

    private final OutboxEventMapper mapper;

    public OutboxScheduler(OutboxEventMapper mapper) {
        this.mapper = mapper;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void scanAndDispatch() {
        List<OutboxWriter.OutboxEventPO> events = mapper.selectList(
            new LambdaQueryWrapper<OutboxWriter.OutboxEventPO>()
                .eq(OutboxWriter.OutboxEventPO::getStatus, "PENDING")
                .le(OutboxWriter.OutboxEventPO::getNextRetryAt, Instant.now())
                .last("LIMIT 100")
        );

        for (OutboxWriter.OutboxEventPO event : events) {
            try {
                // 实际工程：投递到 RabbitMQ / RocketMQ
                dispatchToMQ(event);

                event.setStatus("DISPATCHED");
                mapper.updateById(event);
                log.info("Outbox 事件已投递：type={}, id={}", event.getEventType(), event.getId());
            } catch (Exception e) {
                log.warn("Outbox 事件投递失败：type={}, id={}, retry={}",
                    event.getEventType(), event.getId(), event.getRetryCount(), e);

                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= MAX_RETRY) {
                    event.setStatus("FAILED");  // 死信
                } else {
                    // 指数退避：1s, 2s, 4s, 8s, ...
                    long backoffSeconds = (long) Math.pow(2, event.getRetryCount());
                    event.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
                }
                mapper.updateById(event);
            }
        }
    }

    private void dispatchToMQ(OutboxWriter.OutboxEventPO event) {
        // 实际工程：rabbitTemplate.convertAndSend(...) 或 rocketMQTemplate.send(...)
        // 简化：仅打印日志
        log.info("[MQ] 投递事件：type={}, payload={}", event.getEventType(), event.getPayload());
    }
}