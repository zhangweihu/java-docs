package com.ddd.saga.order.application;

import com.ddd.saga.order.domain.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单应用服务（Saga 协调版）。
 *
 * <p>本类演示 Saga 启动入口：
 * <ol>
 *   <li>创建订单（写入数据库）</li>
 *   <li>启动 Saga（StateMachine 编排）</li>
 *   <li>Saga 协调调用库存端口 + 支付端口</li>
 * </ol>
 *
 * <p>完整 Saga 见 {@code saga/OrderSagaCoordinator}。
 */
@Service
public class OrderAppService {

    @Transactional
    public Long createOrder(Long customerId, BigDecimal amount) {
        String orderNo = "O" + System.currentTimeMillis();
        // 实际工程：用仓储保存 Order
        // 简化：内存存储
        Order order = new Order(System.nanoTime(), orderNo, customerId, amount, Instant.now());
        return order.id();
    }

    @Transactional
    public void markPaid(Long orderId) {
        // 实际工程：findById → markPaid → save
    }

    @Transactional
    public void cancel(Long orderId) {
        // 实际工程：findById → cancel → save
    }
}