package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.common.BusinessException;
import com.mall.common.ResultCode;
import com.mall.dto.CreateOrderDTO;
import com.mall.entity.Order;
import com.mall.entity.Product;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.ProductMapper;
import com.mall.service.OrderService;
import com.mall.util.OrderNoGenerator;
import com.mall.util.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 订单服务实现：下单三件套（面试必问）。
 *   1. @Transactional      保证扣库存、建订单在同一事务，失败整体回滚
 *   2. Redis setnx 分布式锁  防止同一用户对同一商品重复下单
 *   3. SQL 乐观扣减（stock>=n） 并发下保证不超卖，受影响行数=0 视为抢购失败
 *
 * 锁内校验 + 锁外回填（库存扣减放锁内，减少持锁时间）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 订单状态常量：0-待支付 1-已支付 2-已发货 3-已完成 4-已取消 */
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_CANCELLED = 4;

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(Long userId, CreateOrderDTO dto) {
        // 1. 分布式锁：防止同一用户对同一商品并发重复下单
        String lockKey = RedisKeyUtil.orderLock(userId, dto.getProductId());
        String lockValue = UUID.randomUUID().toString();
        boolean locked = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey, lockValue, Duration.ofSeconds(5)));
        if (!locked) {
            throw new BusinessException(ResultCode.FREQUENT_REQUEST);
        }
        try {
            // 2. 乐观扣减库存：SQL 里带 stock >= quantity 条件，天然防超卖
            int affected = productMapper.deductStock(dto.getProductId(), dto.getQuantity());
            if (affected == 0) {
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
            }
            // 3. 读取商品单价，做价格快照
            Product product = productMapper.selectById(dto.getProductId());
            BigDecimal total = product.getPrice().multiply(BigDecimal.valueOf(dto.getQuantity()));

            // 4. 创建订单（order_no 唯一索引兜底幂等）
            Order order = new Order();
            order.setOrderNo(OrderNoGenerator.generate(userId));
            order.setUserId(userId);
            order.setProductId(dto.getProductId());
            order.setPrice(product.getPrice());
            order.setQuantity(dto.getQuantity());
            order.setTotalAmount(total);
            order.setStatus(STATUS_UNPAID);
            orderMapper.insert(order);
            return order.getOrderNo();
        } finally {
            // 5. 用 Lua 脚本释放锁：先比对 value 再删除（原子操作），防止误删他人锁
            //    （锁超时后原 owner 才执行到 finally 的典型场景）
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end";
            DefaultRedisScript<Long> unlockScript = new DefaultRedisScript<>(script, Long.class);
            stringRedisTemplate.execute(unlockScript, java.util.List.of(lockKey), lockValue);
        }
    }

    @Override
    public void payCallback(String orderNo) {
        // 幂等：状态机流转，只有"待支付"能支付成功；重复回调直接返回
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, STATUS_UNPAID)
                .set(Order::getStatus, STATUS_PAID)
                .set(Order::getPayTime, LocalDateTime.now()));
        if (updated == 0) {
            log.warn("支付回调重复或订单状态不允许: {}", orderNo);
        }
    }

    @Override
    public Object detail(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        // 越权防护：订单归属校验，只能查自己的订单
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return order;
    }

    @Override
    public void closeExpiredOrders() {
        // 扫描 30 分钟前仍为"待支付"的订单并取消（生产环境建议按时间分片 + MQ 延迟消息）
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(30);
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getStatus, STATUS_UNPAID)
                .lt(Order::getCreateTime, deadline)
                .set(Order::getStatus, STATUS_CANCELLED));
    }
}
