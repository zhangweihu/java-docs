package com.mall.service.impl;

import com.mall.common.BusinessException;
import com.mall.common.ResultCode;
import com.mall.service.CartService;
import com.mall.util.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 购物车服务实现：Redis Hash 结构，key=mall:cart:{userId}。
 * 为什么选 Hash 而不是 String+JSON？
 *   - 单品数量自增（hincrby）原子完成，无需读改写
 *   - 商品数量变化时只需更新一个字段，带宽小
 *   - 天然按商品分字段，便于后续聚合
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void add(Long userId, Long productId, Integer quantity) {
        String key = RedisKeyUtil.cart(userId);
        stringRedisTemplate.opsForHash().increment(key, String.valueOf(productId), quantity);
    }

    @Override
    public Map<Object, Object> list(Long userId) {
        return stringRedisTemplate.opsForHash().entries(RedisKeyUtil.cart(userId));
    }

    @Override
    public void update(Long userId, Long productId, Integer quantity) {
        String key = RedisKeyUtil.cart(userId);
        if (!stringRedisTemplate.opsForHash().hasKey(key, String.valueOf(productId))) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (quantity <= 0) {
            remove(userId, productId);
        } else {
            stringRedisTemplate.opsForHash().put(key, String.valueOf(productId), String.valueOf(quantity));
        }
    }

    @Override
    public void remove(Long userId, Long productId) {
        stringRedisTemplate.opsForHash().delete(RedisKeyUtil.cart(userId), String.valueOf(productId));
    }

    @Override
    public void clear(Long userId) {
        stringRedisTemplate.delete(RedisKeyUtil.cart(userId));
    }
}
