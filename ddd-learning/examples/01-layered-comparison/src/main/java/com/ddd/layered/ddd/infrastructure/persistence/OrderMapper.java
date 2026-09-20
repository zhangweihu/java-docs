package com.ddd.layered.ddd.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 订单 MyBatis Mapper。 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderPO> {
}