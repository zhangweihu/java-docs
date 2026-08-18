package com.mall.order.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.BusinessException;
import com.mall.common.Result;
import com.mall.common.feign.ProductClient;
import com.mall.common.feign.UserClient;
import com.mall.order.entity.Order;
import com.mall.order.mapper.OrderMapper;
import com.mall.order.service.OrderService;
import com.mall.order.sentinel.OrderSentinelBlockHandler;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 订单服务实现：Seata AT 模式跨服务事务演示。
 *
 * Seata AT 原理（两步）：
 *   - 分支事务执行前：记录 undo_log（数据快照）
 *   - 全局提交/回滚：提交则删 undo_log；回滚则用 undo_log 反向补偿，
 *     对业务代码零侵入（只需 @GlobalTransactional 注解）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ProductClient productClient;  // Feign：调用商品服务
    private final UserClient userClient;        // Feign：调用用户服务

    @Override
    @GlobalTransactional(name = "mall-create-order", rollbackFor = Exception.class)
    @SentinelResource(value = "order:create",
            blockHandlerClass = OrderSentinelBlockHandler.class,
            blockHandler = "createOrderBlock",
            fallbackClass = OrderSentinelBlockHandler.class,
            fallback = "createOrderFallback")
    public Result<String> createOrder(Long userId, Long productId, Integer quantity) {
        // 1. 通过 Feign 获取商品单价（跨服务调用，经 Nacos 服务发现）
        Result<BigDecimal> priceResult = productClient.getPrice(productId);
        if (!priceResult.isSuccess()) {
            throw new BusinessException(priceResult.getCode(), priceResult.getMessage());
        }
        // 2. 通过 Feign 扣减商品库存（操作商品库，Seata 分支事务）
        Result<Void> deduct = productClient.deductStock(productId, quantity);
        if (!deduct.isSuccess()) {
            // 库存不足 -> 抛异常 -> 全局回滚（商品库与订单库一致回退）
            throw new BusinessException(deduct.getCode(), deduct.getMessage());
        }
        // 3. 本地插入订单（操作订单库，Seata 分支事务）
        Order order = new Order();
        order.setOrderNo(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6));
        order.setUserId(userId);
        order.setProductId(productId);
        order.setPrice(priceResult.getData());
        order.setQuantity(quantity);
        order.setTotalAmount(priceResult.getData().multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(0);
        orderMapper.insert(order);
        log.info("下单成功 orderNo={}, 跨服务调用用户昵称={}",
                order.getOrderNo(), userClient.getNickname(userId).getData());
        return Result.ok(order.getOrderNo());
    }

    @Override
    public Result<Order> detail(String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        return order == null ? Result.fail(404, "订单不存在") : Result.ok(order);
    }
}
