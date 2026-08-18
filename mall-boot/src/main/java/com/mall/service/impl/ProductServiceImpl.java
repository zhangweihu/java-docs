package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.BusinessException;
import com.mall.common.ResultCode;
import com.mall.entity.Product;
import com.mall.mapper.ProductMapper;
import com.mall.service.ProductService;
import com.mall.util.RedisKeyUtil;
import com.mall.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 商品服务实现：演示缓存治理核心三件套。
 *   1. Cache Aside：先读缓存 -> 未命中读 DB -> 回填缓存（JSON 序列化）
 *   2. 互斥锁：缓存击穿时只允许一个线程重建缓存（setIfAbsent 实现 setnx）
 *   3. 随机 TTL：基础 TTL + 随机抖动，避免缓存同时过期造成雪崩
 *   4. 空值缓存：查询不存在时缓存空串 5 分钟，防穿透
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String MUTEX_PREFIX = "mall:lock:product:";

    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<ProductVO> list() {
        List<Product> list = productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1));
        return list.stream().map(this::toVO).toList();
    }

    @Override
    public ProductVO detail(Long id) {
        String key = RedisKeyUtil.productDetail(id);
        // 1. 先查缓存
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (cached.isEmpty()) { // 命中空值缓存：商品不存在
                throw new BusinessException(ResultCode.NOT_FOUND);
            }
            return deserialize(cached);
        }

        // 2. 缓存未命中：互斥锁防击穿（同一商品只允许一个线程回源 DB）
        String mutexKey = MUTEX_PREFIX + id;
        boolean locked = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                mutexKey, "1", Duration.ofSeconds(10)));
        if (!locked) {
            // 拿不到锁：说明别的线程正在重建缓存，短暂等待后递归重试
            sleepQuietly(50);
            return detail(id);
        }
        try {
            // 3. 双检查：拿到锁后可能缓存已被别的线程重建
            cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return cached.isEmpty() ? null : deserialize(cached);
            }
            // 4. 回源 DB
            Product product = productMapper.selectById(id);
            if (product == null || product.getStatus() != 1) {
                stringRedisTemplate.opsForValue().set(key, "", Duration.ofMinutes(5));
                throw new BusinessException(ResultCode.NOT_FOUND);
            }
            // 5. 回填缓存：基础 TTL 30 分钟 + 随机 0~300 秒抖动，防雪崩
            long ttl = 1800 + (long) (Math.random() * 300);
            stringRedisTemplate.opsForValue().set(key, serialize(toVO(product)), Duration.ofSeconds(ttl));
            return toVO(product);
        } finally {
            stringRedisTemplate.delete(mutexKey); // 释放互斥锁
        }
    }

    @Override
    public void add(Product product) {
        if (product.getStock() == null || product.getStock() < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        productMapper.insert(product);
        // 新增后主动删除旧缓存，保证下次读取为最新数据（Cache Aside 的删除策略）
        stringRedisTemplate.delete(RedisKeyUtil.productDetail(product.getId()));
    }

    private String serialize(ProductVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    private ProductVO deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProductVO.class);
        } catch (Exception e) {
            return null; // 序列化格式异常时降级为缓存未命中
        }
    }

    private ProductVO toVO(Product product) {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        return vo;
    }

    private void sleepQuietly(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
