package com.mall.service;

import java.util.Map;

/**
 * 购物车服务接口：基于 Redis Hash 存储（字段=商品ID，值=数量）。
 * 购物车数据量小、读写频繁、允许短暂丢失，非常适合放 Redis。
 */
public interface CartService {

    /** 加入购物车（数量累加） */
    void add(Long userId, Long productId, Integer quantity);

    /** 查看购物车（商品ID -> 数量） */
    Map<Object, Object> list(Long userId);

    /** 修改数量 */
    void update(Long userId, Long productId, Integer quantity);

    /** 删除某项 */
    void remove(Long userId, Long productId);

    /** 清空购物车（下单成功后调用） */
    void clear(Long userId);
}
