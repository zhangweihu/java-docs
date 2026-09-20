package com.ddd.saga.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox Writer（业务事务内写 outbox_event）。
 *
 * <p>使用 Outbox 模式代替同步事件发布：
 * <ol>
 *   <li>业务事务内调用 {@link #write}，将事件写入 outbox_event 表</li>
 *   <li>业务事务提交</li>
 *   <li>后台 {@link OutboxScheduler} 扫描 PENDING 事件投递到 MQ</li>
 * </ol>
 */
@Component
public class OutboxWriter {

    private final OutboxEventMapper mapper;

    public OutboxWriter(OutboxEventMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 在业务事务内写 outbox。
     *
     * <p>注意：{@code Propagation.MANDATORY} 确保调用方已开事务，
     * 如果没有事务则抛异常（避免"outbox 写了但业务回滚"的不一致）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String eventType, String aggregateId, String payload) {
        OutboxEventPO po = new OutboxEventPO();
        po.setEventType(eventType);
        po.setAggregateId(aggregateId);
        po.setPayload(payload);
        po.setStatus("PENDING");
        po.setRetryCount(0);
        po.setNextRetryAt(Instant.now());
        po.setCreatedAt(Instant.now());
        mapper.insert(po);
    }

    /** 简化 Outbox PO（仅演示核心字段）。 */
    @Data
    @TableName("outbox_event")
    public static class OutboxEventPO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String eventType;
        private String aggregateId;
        private String payload;
        private String status;
        private Integer retryCount;
        private Instant nextRetryAt;
        private Instant createdAt;
    }
}