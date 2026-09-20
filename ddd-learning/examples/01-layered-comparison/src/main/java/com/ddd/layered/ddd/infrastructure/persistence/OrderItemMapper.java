package com.ddd.layered.ddd.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 订单明细 MyBatis Mapper。 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemPO> {
}