# 第七十九章 跨上下文集成（Saga / Outbox / 可观测）

> **本章目标**：把订单 / 库存 / 支付三个上下文在分层架构下集成，掌握 Saga 协调、Outbox 模式、可观测性设计。
>
> **前置知识**：第 76~78 章（订单/库存/支付领域实战）、第 46 章（分布式事务与 Saga）、第 43 章（CQRS/Outbox）、第 48 章（可观测）。

> 一句话：**跨上下文集成的核心是"用 Saga 替代分布式事务 + 用 Outbox 替代同步事件 + 用幂等键保证安全"**。

---

## 79.1 跨上下文集成的三种范式

### 79.1.1 直接调用（同步 RPC）

```java
// 订单上下文同步调用支付上下文的"冻结"接口
paymentClient.freeze(userId, amount);
```

**优点**：实现简单、强一致。

**缺点**：

- 调用方与被调用方强耦合
- 远程调用慢、易超时
- 一方挂了整个事务失败

### 79.1.2 事件驱动（异步）

```java
// 订单发布事件，支付订阅
events.publishEvent(new OrderPaidEvent(orderId));
```

**优点**：解耦、容错、可扩展。

**缺点**：最终一致、Saga 协调复杂。

### 79.1.3 Saga（编排式 / 协同式）

```
Saga 协调者管理步骤与补偿，分布式事务的"软替代"。
```

### 79.1.4 选型决策

| 场景 | 推荐方案 |
| --- | --- |
| 同进程同库 | 单一数据库事务 |
| 同进程不同库 | Saga（本地消息表） |
| 跨进程同业务域 | Saga + Outbox |
| 跨进程跨业务域 | Saga + Outbox + MQ |
| 强一致要求高 | 评估是否真的需要分布式 |

---

## 79.2 上下文映射：防腐层（ACL）在集成中的位置

### 79.2.1 防腐层的归属原则

> **防腐层接口归"调用方"所有**，实现归"被调用方"或专门的"适配层"。

订单上下文调用支付上下文的"冻结金额"接口：

```java
// 订单上下文定义端口（接口）
package com.ddd.ecommerce.order.application.api;

public interface PaymentPort {
    String freeze(Long userId, Money amount, String idempotencyKey, String businessOrderId);
    void unfreeze(String transactionId);
    void deduct(String transactionId);
}
```

支付上下文实现端口（同进程调用或远程 Feign）：

```java
// 支付上下文的 PaymentPortAdapter
@Component
public class PaymentPortAdapter implements PaymentPort {
    private final PaymentAppService paymentService;

    @Override
    public String freeze(Long userId, Money amount, String idempotencyKey, String businessOrderId) {
        return paymentService.freeze(new FreezeCommand(userId, amount,
            new IdempotencyKey(idempotencyKey), businessOrderId));
    }
    // ... 其他方法
}
```

---

## 79.3 事件总线：进程内 / RabbitMQ / RocketMQ

### 79.3.1 进程内（ApplicationEventPublisher）

```java
@Service
public class OrderAppService {
    private final ApplicationEventPublisher events;

    public OrderId submit(SubmitOrderCommand cmd) {
        // ... 业务逻辑
        events.publishEvent(new OrderCreatedEvent(order.id(), Instant.now()));
        return order.id();
    }
}
```

**适用**：单进程、调试阶段。

### 79.3.2 RabbitMQ（跨进程可靠投递）

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

```java
@Component
public class RabbitMQConfig {
    @Bean
    public Queue orderCreatedQueue() {
        return new Queue("order.created.queue");
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder
            .bind(orderCreatedQueue())
            .to(new TopicExchange("order.events"))
            .with("order.created");
    }
}
```

### 79.3.3 RocketMQ（事务消息）

RocketMQ 3.0+ 提供事务消息，可保证"本地事务 + 消息投递"的强一致：

```java
// 生产者
rocketMQTemplate.sendMessageInTransaction(
    "order-topic",
    MessageBuilder.withPayload(orderCreatedEvent).build(),
    null
);
```

### 79.3.4 选型决策

| 场景 | 推荐 |
| --- | --- |
| 单进程 | ApplicationEventPublisher |
| 跨进程 + 中小规模 | RabbitMQ |
| 跨进程 + 大规模 + 严格事务 | RocketMQ 事务消息 |
| 云原生 | 云厂商托管（如 AWS EventBridge） |

---

## 79.4 Outbox 模式：业务事务 + 事件表 + 后台投递

### 79.4.1 模式动机

同步发布事件的问题：

- 业务事务提交后事件发布失败 → 业务和数据不一致
- 事件订阅者在同一事务中 → 订阅者失败导致业务回滚

Outbox 模式：

```
业务事务 ─┬─ 写业务表（Order）
          └─ 写 outbox_event 表（同事务）
                            │
                            ▼
            后台 OutboxScheduler 定时扫描
                            │
                            ▼
            投递到 MQ / 调用订阅者
                            │
                            ▼
            删除 / 标记 outbox_event 为已投递
```

### 79.4.2 outbox_event 表结构

```sql
CREATE TABLE outbox_event (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    event_type    VARCHAR(64)  NOT NULL,        -- OrderCreatedEvent 等
    aggregate_id  VARCHAR(64)  NOT NULL,        -- orderId 等
    payload       TEXT         NOT NULL,        -- 事件 JSON
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    retry_count   INT          NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_status_next_retry (status, next_retry_at)
);
```

### 79.4.3 OutboxWriter（业务事务内写）

```java
@Component
public class OutboxWriter {

    private final OutboxEventMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)  // 必须存在业务事务
    public void write(DomainEvent event) {
        OutboxEventPO po = new OutboxEventPO();
        po.setEventType(event.getClass().getSimpleName());
        po.setAggregateId(event.aggregateId());
        po.setPayload(JsonUtils.toJson(event));
        po.setStatus("PENDING");
        po.setNextRetryAt(Instant.now());
        mapper.insert(po);
    }
}
```

### 79.4.4 OutboxScheduler（后台扫描投递）

```java
@Component
public class OutboxScheduler {

    private final OutboxEventMapper mapper;
    private final RabbitTemplate rabbit;

    @Scheduled(fixedDelay = 5000)   // 每 5 秒
    @Transactional
    public void scanAndDispatch() {
        List<OutboxEventPO> events = mapper.selectList(
            new LambdaQueryWrapper<OutboxEventPO>()
                .eq(OutboxEventPO::getStatus, "PENDING")
                .le(OutboxEventPO::getNextRetryAt, Instant.now())
                .last("LIMIT 100")
        );

        for (OutboxEventPO event : events) {
            try {
                // 投递到 MQ
                rabbit.convertAndSend(
                    event.getEventType().toLowerCase() + ".exchange",
                    "",
                    event.getPayload()
                );

                // 标记为已投递
                event.setStatus("DISPATCHED");
                mapper.updateById(event);
            } catch (Exception e) {
                // 失败重试（指数退避）
                event.setRetryCount(event.getRetryCount() + 1);
                event.setNextRetryAt(Instant.now().plusSeconds((long) Math.pow(2, event.getRetryCount())));
                mapper.updateById(event);

                if (event.getRetryCount() > 10) {
                    event.setStatus("FAILED");   // 进入死信
                    mapper.updateById(event);
                }
            }
        }
    }
}
```

### 79.4.5 应用服务集成 Outbox

```java
@Service
public class OrderAppService {
    private final OrderRepository repository;
    private final OutboxWriter outbox;

    @Transactional
    public OrderId submit(SubmitOrderCommand cmd) {
        // 1. 业务逻辑
        Order order = Order.submit(/* ... */);
        repository.save(order);

        // 2. 写 outbox（同一事务）
        outbox.write(new OrderCreatedEvent(order.id(), order.orderNo(), Instant.now()));

        return order.id();
    }
}
```

---

## 79.5 Saga 编排式 / 协同式 在分层架构下的实现

### 79.5.1 编排式 Saga（Orchestration）

中央协调者管理所有步骤：

```
┌────────────────────────────────┐
│   SagaCoordinator (编排器)     │
│                                │
│  步骤 1: 库存预留               │
│  步骤 2: 金额冻结               │
│  步骤 3: 等待 OrderPaid 事件    │
│  步骤 4: 扣款                   │
│  步骤 5: 库存扣减               │
│  步骤 6: 完成                   │
│                                │
│  补偿 1: 库存释放               │
│  补偿 2: 金额解冻               │
│  补偿 3: -                     │
│  补偿 4: 退款                   │
│  补偿 5: 库存释放               │
│  补偿 6: -                     │
└────────────────────────────────┘
```

```java
public class OrderSagaStateMachine {

    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> factory;
    private final SagaStepExecutor executor;
    private final SagaStepCompensator compensator;

    public void execute(PlaceOrderSaga saga) {
        StateMachine<OrderSagaState, OrderSagaEvent> sm =
            factory.getStateMachine(saga.getId());

        // 启动 Saga
        sm.sendEvent(OrderSagaEvent.START);

        // 执行各步骤（每步成功 → 下一事件；失败 → 触发补偿）
        try {
            executor.executeStep(saga, OrderSagaState.RESERVE_INVENTORY);
            sm.sendEvent(OrderSagaEvent.INVENTORY_RESERVED);

            executor.executeStep(saga, OrderSagaState.FREEZE_AMOUNT);
            sm.sendEvent(OrderSagaEvent.AMOUNT_FROZEN);

            // 等待 OrderPaid 事件（外部触发）
            // ...

            executor.executeStep(saga, OrderSagaState.DEDUCT_AMOUNT);
            sm.sendEvent(OrderSagaEvent.AMOUNT_DEDUCTED);

            // 完成
            sm.sendEvent(OrderSagaEvent.COMPLETED);
        } catch (SagaStepFailedException e) {
            // 触发补偿
            compensator.compensate(saga, e.getFailedStep());
        }
    }
}
```

### 79.5.2 协同式 Saga（Choreography）

无中央协调者，每个服务订阅事件后自行决定下一步：

```
[订单] 创建订单 ──► 发布 OrderCreated 事件
                          │
                          ▼
                    [库存] 订阅：reserve ──► 发布 InventoryReserved 事件
                                                  │
                                                  ▼
                                            [支付] 订阅：freeze ──► 发布 AmountFrozen 事件
                                                                              │
                                                                              ▼
                                                                       [订单] 等待用户付款 → 发布 OrderPaid 事件
                                                                                                      │
                                                                                                      ▼
                                                                                                [支付] deduct ──► 发布 AmountDeducted 事件
                                                                                                                              │
                                                                                                                              ▼
                                                                                                                       [库存] commit
```

**优点**：无单点故障、易扩展。

**缺点**：流程分散在多个服务、调试困难、循环依赖风险。

---

## 79.6 实战：下单 → 库存预留 → 支付扣款的 Saga 全流程

### 79.6.1 Saga 状态机（基于 Spring StateMachine）

```java
@Configuration
@EnableStateMachineFactory(name = "orderSagaFactory")
public class OrderSagaStateMachineConfig
        extends StateMachineConfigurerAdapter<OrderSagaState, OrderSagaEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<OrderSagaState, OrderSagaEvent> states) {
        states.withStates()
            .initial(OrderSagaState.STARTED)
            .state(OrderSagaState.INVENTORY_RESERVED)
            .state(OrderSagaState.AMOUNT_FROZEN)
            .state(OrderSagaState.PAID)
            .state(OrderSagaState.AMOUNT_DEDUCTED)
            .state(OrderSagaState.INVENTORY_COMMITTED)
            .end(OrderSagaState.COMPLETED)
            .end(OrderSagaState.CANCELLED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderSagaState, OrderSagaEvent> transitions) {
        transitions
            .withExternal().source(OrderSagaState.STARTED).target(OrderSagaState.INVENTORY_RESERVED)
                .event(OrderSagaEvent.RESERVE_INVENTORY)
            .and().withExternal().source(OrderSagaState.INVENTORY_RESERVED).target(OrderSagaState.AMOUNT_FROZEN)
                .event(OrderSagaEvent.FREEZE_AMOUNT)
            .and().withExternal().source(OrderSagaState.AMOUNT_FROZEN).target(OrderSagaState.PAID)
                .event(OrderSagaEvent.ORDER_PAID)
            // ... 完整配置
            .and().withExternal().source(OrderSagaState.STARTED).target(OrderSagaState.CANCELLED)
                .event(OrderSagaEvent.CANCEL);
    }
}
```

### 79.6.2 Saga 协调者

```java
@Service
public class OrderSagaCoordinator {

    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> factory;
    private final InventoryPort inventoryPort;
    private final PaymentPort paymentPort;

    public void placeOrder(PlaceOrderSaga saga) {
        StateMachine<OrderSagaState, OrderSagaEvent> sm =
            factory.getStateMachine(saga.getId());

        sm.stop();
        sm.getExtendedState().getVariables().put("saga", saga);
        sm.start();

        try {
            // 步骤 1：库存预留
            inventoryPort.reserve(/* ... */);
            sm.sendEvent(OrderSagaEvent.RESERVE_INVENTORY);

            // 步骤 2：金额冻结
            String transactionId = paymentPort.freeze(/* ... */);
            saga.setTransactionId(transactionId);
            sm.sendEvent(OrderSagaEvent.FREEZE_AMOUNT);

            // ... 等待 OrderPaid 事件（异步）
        } catch (Exception e) {
            compensate(saga, e);
        }
    }

    @Transactional
    public void onOrderPaid(OrderPaidEvent event) {
        // ... 继续 Saga
    }

    private void compensate(PlaceOrderSaga saga, Exception e) {
        // 补偿逻辑：释放库存、解冻金额
    }
}
```

---

## 79.7 可观测：Saga 日志、补偿日志、死信处理

### 79.7.1 Saga 日志表

```sql
CREATE TABLE saga_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    saga_id      VARCHAR(64)  NOT NULL,
    saga_type    VARCHAR(64)  NOT NULL,
    step_name    VARCHAR(64)  NOT NULL,
    step_status  VARCHAR(16)  NOT NULL,    -- PENDING / SUCCESS / FAILED / COMPENSATED
    payload      TEXT,
    error_msg    TEXT,
    created_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_saga_id (saga_id)
);
```

### 79.7.2 Saga 步骤日志记录

```java
public class SagaLogger {

    private final SagaLogMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void logStart(String sagaId, String stepName, Object payload) {
        SagaLogPO log = new SagaLogPO();
        log.setSagaId(sagaId);
        log.setStepName(stepName);
        log.setStepStatus("PENDING");
        log.setPayload(JsonUtils.toJson(payload));
        mapper.insert(log);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void logSuccess(String sagaId, String stepName) {
        // update status = SUCCESS
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void logFailed(String sagaId, String stepName, String error) {
        // update status = FAILED + error_msg
    }
}
```

### 79.7.3 死信处理

```java
@Component
public class DeadLetterListener {

    private final SagaLogMapper logMapper;
    private final AlertService alertService;

    @RabbitListener(queues = "order.saga.dead-letter.queue")
    public void onDeadLetter(OutboxEventPO event) {
        logMapper.insert(/* Saga 失败日志 */);
        alertService.sendAlert("Saga dead-letter: " + event.getEventType());
    }
}
```

---

## 79.8 单元 + 集成测试

### 79.8.1 单元测试：Saga 状态机迁移

```java
class OrderSagaStateMachineTest {

    @Test
    void should_transition_through_all_steps() {
        StateMachine<OrderSagaState, OrderSagaEvent> sm = factory.getStateMachine();
        sm.sendEvent(OrderSagaEvent.RESERVE_INVENTORY);
        assertEquals(OrderSagaState.INVENTORY_RESERVED, sm.getState().getId());
        // ... 完整测试
    }
}
```

### 79.8.2 集成测试：完整 Saga

```java
@SpringBootTest
class OrderSagaIntegrationTest {

    @Autowired private OrderAppService orderAppService;
    @Autowired private InventoryAppService inventoryAppService;
    @Autowired private PaymentAppService paymentAppService;
    @MockBean private ExternalUserServiceClient userClient;

    @Test
    void should_complete_full_saga() {
        // 1. 创建订单
        Long orderId = orderAppService.submit(new SubmitOrderCommand(1L, items)).value();

        // 2. 模拟付款
        orderAppService.pay(new OrderId(orderId));

        // 3. 验证订单状态
        Order order = orderAppService.findById(new OrderId(orderId));
        assertEquals(OrderStatus.PAID, order.status());
    }

    @Test
    void should_compensate_when_payment_fails() {
        // Mock 支付失败
        when(paymentClient.freeze(any(), any(), any(), any()))
            .thenThrow(new InsufficientBalanceException(1L));

        // 验证补偿逻辑
        // 1. 创建订单
        // 2. 支付失败 → 触发补偿：释放库存
        // 3. 验证库存恢复
    }
}
```

### 本章小结

- 跨上下文集成三种范式：直接调用 / 事件驱动 / Saga。
- 防腐层接口归调用方所有。
- Outbox 模式保证"业务事务 + 事件投递"原子性。
- Saga 有编排式（中心化）和协同式（去中心化）两种。
- Saga 日志 + 死信处理是生产级必备。

### 面试高频问题清单（79 章）

1. 直接调用、事件驱动、Saga 三种范式的区别？
2. Outbox 模式的核心思想是什么？为什么需要它？
3. 编排式 vs 协同式 Saga 的优缺点？
4. Saga 日志表应该记录哪些字段？
6. 跨上下文调用的防腐层接口归谁所有？
7. 死信处理如何避免消息永久失败？
8. 如何测试 Saga？

### 练习题

1. 为你的项目设计一个简单的 Saga（如"下单-支付"），用状态机实现。
2. 用 Outbox 模式改造一个"业务事务 + 事件发布"场景。
3. 设计 SagaLog 表结构 + SagaLogger 组件。
4. 写一个集成测试：模拟 Saga 全流程成功 + 失败补偿两条路径。
5. 思考：编排式 Saga 的中心点故障如何处理？

### 配套

- **前置**：第 76~78 章（订单/库存/支付领域实战）、第 46 章（分布式事务与 Saga）
- **后续**：第 80 章（专题收官）

---
至此，第七十九章 跨上下文集成 Saga/Outbox 学习完成。下一章 [80-专题收官演进路线.md](./80-专题收官演进路线.md) ｜ 返回：[README.md](./README.md)