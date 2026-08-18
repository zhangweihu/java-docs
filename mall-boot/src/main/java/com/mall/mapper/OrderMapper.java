package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.Order;

/**
 * 订单 Mapper：继承 BaseMapper 即可。
 * 状态流转（如超时关单）可在 Service 层配合 UpdateWrapper 完成。
 */
public interface OrderMapper extends BaseMapper<Order> {
}
