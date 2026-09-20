package com.ddd.saga.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** Outbox Mapper。 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxWriter.OutboxEventPO> {
}