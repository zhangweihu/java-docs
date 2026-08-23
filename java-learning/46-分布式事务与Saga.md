# 第四十六章 微服务下分布式事务与 Saga（从强一致到最终一致）

> 本章目标：理解微服务拆分后本地事务为何失效，掌握分布式事务六大方案（2PC/XA、TCC、本地消息表、MQ 事务消息、Saga、Seata AT）的原理与代码形态，重点吃透 Saga 的编排式与协同式两种实现及补偿机制，能独立完成"下单 → 扣库存 → 扣款"的 Saga 实战，并做出选型决策。
>
> 前置知识：第三十三章 MySQL（本地事务、锁）、第四十一章 DDD（聚合边界决定事务边界）、第四十二章 Spring StateMachine（Saga 状态机化）、第四十三章 CQRS（事件驱动）、第四十五章 Axon（Saga 的框架实现）。

## 46.1 问题：微服务拆了，事务怎么办

### 46.1.1 本地事务的边界

单体时代，一次下单是一个 `@Transactional`：订单表、库存表、账户表在**同一个数据库**里，ACID 由数据库保证：

```java
@Transactional
public void createOrder(OrderDTO dto) {
    orderMapper.insert(order);        // 写订单
    inventoryMapper.deduct(dto.skuId(), dto.count());   // 扣库存
    accountMapper.deduct(dto.userId(), dto.amount());   // 扣款
    // 任何一个失败，全部回滚
}
```

微服务拆分后（第 32 章 mall-cloud：order-service / inventory-service / account-service），三个操作分属**三个数据库、三个进程**：

```
order-service 下单 ──HTTP/Feign──► inventory-service 扣库存
        │
        └──HTTP/Feign──► account-service 扣款
```

问题来了：
- 订单写成功了，扣库存失败——**订单库已提交，无法回滚**；
- 每个服务自己的 `@Transactional` 只能保证**本地**原子性；
- 跨服务没有统一的回滚协调者，分布式环境还叠加**网络超时、服务宕机、重复请求**。

> **一句话记忆**：本地事务靠数据库，分布式事务靠"约定"——要么约定大家一起提交（强一致），要么约定失败大家一起补偿（最终一致）。

### 46.1.2 重新审视：哪些"事务"真的需要跨服务？

先泼冷水：**很多跨服务"事务"是伪需求**。拆分微服务前问三个问题（呼应第 44 章事件风暴的聚合划分）：

| 问题 | 如果答案是 | 结论 |
| --- | --- | --- |
| 两步必须**同生共死**（同事务）吗？ | 是 | 说明它们可能属于**同一个聚合**，不该拆开（第 41 章铁律） |
| 可以**先做 A，再慢慢做 B** 吗？ | 可以 | 最终一致即可，别上分布式事务 |
| 是"账务"类强一致业务（转账）吗？ | 是 | 才值得上 2PC/TCC 这类强一致方案 |

**绝大多数业务（下单、发券、通知）本质是"先 A 后 B，失败补偿"**，对应最终一致方案；只有资金、库存强扣减等极少数场景才需要强一致。**先砍掉伪需求，再谈方案**。

## 46.2 一致性模型：CAP 与 BASE

### 46.2.1 CAP 定理在分布式事务中的含义

```
CAP：一致性（C）/ 可用性（A）/ 分区容错（P）三选二
分布式系统 P 必选（网络分区必然存在），所以在 C 和 A 之间权衡：
  ├─ 强一致（CP）：分区时宁可拒绝服务，也要保证数据一致（2PC、ZooKeeper）
  └─ 最终一致（AP）：分区时保持可用，数据慢慢一致（Saga、MQ 异步、缓存）
```

> 注意：CAP 是"**网络分区发生瞬间**"的取舍，不是日常状态。日常系统大多数时间 C 和 A 都能满足，只有断网/宕机时才被迫二选一。别把 CAP 当"不用做分布式事务"的挡箭牌。

### 46.2.2 BASE 与最终一致

**BASE** = Basically Available（基本可用）+ Soft state（软状态）+ Eventually consistent（最终一致）。分布式事务方案大多落在 BASE 上：**不追求任何时刻一致，追求"最终某个时刻达成一致"**。

| 维度 | 强一致（ACID） | 最终一致（BASE） |
| --- | --- | --- |
| 转账后立刻查余额 | 一定看到新余额 | 可能短暂看到旧值 |
| 扣库存 | 库存扣减即刻可见 | 下单后片刻才扣成功 |
| 实现 | 2PC/XA、单库事务 | TCC、本地消息表、MQ 事务、Saga、Seata AT |
| 适用 | 账务、支付、库存强扣减 | 通知、积分、报表、大部分订单流程 |
| 代价 | 可用性下降、性能差、实现复杂 | 需要补偿、幂等、对账兜底 |

## 46.3 六大方案全景（先看地图）

| 方案 | 一致性 | 侵入性 | 性能 | 适用 | 代表 |
| --- | --- | --- | --- | --- | --- |
| **2PC / XA** | 强一致 | 高（数据库/中间件支持） | 低（阻塞） | 银行核心、同构数据库 | Atomikos、Seata XA |
| **TCC** | 强一致（业务补偿） | 高（三方法+空回滚） | 中 | 资金、库存强扣减 | Seata TCC、tcc-transaction |
| **本地消息表** | 最终一致 | 中 | 高 | 老系统、无事务消息中间件 | 自研 |
| **MQ 事务消息** | 最终一致 | 中 | 高 | 下单发券等异步解耦 | RocketMQ |
| **Saga** | 最终一致（补偿） | 中 | 高 | 长流程、跨多服务 | Seata Saga、Axon、自研 |
| **Seata AT** | 最终一致（近似强） | 低（无侵入） | 中 | 存量项目快速接入 | Seata |

> 六个方案按"**强一致 → 最终一致**"排列。实际选型常组合：**核心资金用 TCC，一般业务用 MQ/Saga**。

## 46.4 2PC / XA：两阶段提交

### 46.4.1 原理

引入**协调者（Coordinator）**，两阶段让所有参与者要么全提交、要么全回滚：

```
阶段一（准备 Prepare）：
  协调者 ──prepare──► 参与者A ──可以提交？──► 记录 undo/redo 日志
  协调者 ──prepare──► 参与者B ──可以提交？──► 记录 undo/redo 日志
  所有参与者回复 OK 或 失败

阶段二（提交/回滚 Commit/Abort）：
  全部 OK ──► 协调者 ──commit──► 所有参与者提交（写失败会重试直到成功）
  任一失败 ──► 协调者 ──abort──► 所有参与者回滚
```

### 46.4.2 致命缺点（为什么"理论完美、实践少见"）

| 问题 | 说明 |
| --- | --- |
| **同步阻塞** | 准备阶段资源被锁定，直到第二阶段结束，长事务拖垮并发 |
| **协调者单点** | 协调者挂了，所有参与者永远挂着资源（无超时机制） |
| **数据不一致窗口** | 第二阶段部分提交成功、部分失败，协调者无法强制纠正 |
| **网络分区不可用** | 分区时协调者联系不上参与者，只能一直等（违反 CAP 的 A） |

### 46.4.3 使用场景

- 同构数据库、规模可控、对强一致要求极高（银行核心、券商清算）；
- 中间件支持（MySQL XA、Oracle、PostgreSQL）；
- Java 侧用 JTA/Atomikos 接入；Seata 的 XA 模式是对 2PC 的改良（有超时、恢复机制）。

> **一句话**：2PC 是分布式事务的"教科书方案"，现实中因阻塞与单点被 TCC/Saga 替代，但理解它是理解一切的基础。

## 46.5 TCC：业务层两阶段（Try-Confirm-Cancel）

### 46.5.1 思想：把"资源锁定"从数据库挪到业务层

TCC 把一次分布式操作拆成三个业务方法（对每个参与资源都要实现）：

| 阶段 | 含义 | 订单场景（扣库存） |
| --- | --- | --- |
| **Try** | 尝试：预留资源（不真正扣减） | 冻结库存 5 件：`freeze(stock, 5)` |
| **Confirm** | 确认：真正提交 | 扣减冻结的 5 件：`deductFrozen(stock, 5)` |
| **Cancel** | 取消：释放预留 | 解冻：`unfreeze(stock, 5)` |

```java
// 库存服务的 TCC 实现
public class InventoryTccAction {

    @TccAction  // 资源预留
    public boolean tryDeduct(Long skuId, int count) {
        // 不能直接 stock = stock - count（失败无法恢复）
        // 正确做法：新增 frozen 字段，先冻结
        return inventoryMapper.tryFreeze(skuId, count);   // frozen = frozen + count
    }

    @TccConfirm  // 确认扣减
    public boolean confirmDeduct(Long skuId, int count) {
        return inventoryMapper.freezeToDeduct(skuId, count); // stock -= count; frozen -= count
    }

    @TccCancel  // 释放预留
    public boolean cancelDeduct(Long skuId, int count) {
        return inventoryMapper.unfreeze(skuId, count);       // frozen -= count
    }
}
```

### 46.5.2 TCC 的三大经典坑（面试必问）

1. **空回滚**：Try 还没执行（网络超时/服务没收到），Cancel 却先到了——Cancel 必须能**幂等识别**并直接成功（记录事务状态）；
2. **悬挂**：Cancel 先于 Try 完成，Try 后到——Try 要检查"是否已 Cancel"，已取消则**拒绝执行**（防悬挂）；
3. **幂等**：Confirm/Cancel 可能被重试多次——必须幂等（以事务 id 去重）。

```java
// 用事务控制表解决三坑（每个 TCC 参与者一张表）
@TccCancel
public boolean cancelDeduct(Long txId, Long skuId, int count) {
    int updated = tccLogMapper.insertIfAbsent(txId, "CANCELED"); // 空回滚幂等：已存在直接成功
    if (updated == 0) return true;
    return inventoryMapper.unfreeze(skuId, count);
}
```

### 46.5.3 优缺点

- ✅ 强一致、性能优于 2PC（Try 不锁死整行）、业务可控性强；
- ❌ **侵入性最强**：每个参与服务都要写 Try/Confirm/Cancel 三套逻辑，还要处理三坑；字段要加 frozen 等预留字段。

> **一句话**：TCC 是"强一致"里最实用的方案，但业务改造量最大——**资金级、库存强扣减**这类才值得。

## 46.6 本地消息表：最早的最终一致方案

### 46.6.1 原理

把"发消息"和"业务操作"放在**同一个本地事务**里，消息表做可靠投递的中转：

```
下单服务（本地事务）：
  ① 写订单表
  ② 写本地消息表（status=PENDING）
  两个写在同一事务，原子成功
后台定时任务：
  ③ 扫 PENDING 消息 ──► 发送给库存服务（MQ/Feign）
  ④ 库存服务确认收到 ──► 消息表置 SUCCESS
  ⑤ 发送失败/超时 ──► 定时重试（幂等）
```

```java
@Transactional
public void createOrder(OrderDTO dto) {
    orderMapper.insert(order);
    messageMapper.insert(new OrderMessage(order.getId(), "PENDING"));  // 同事务
}

@Scheduled(fixedDelay = 5000)  // 定时扫表重试
public void scanAndSend() {
    for (OrderMessage msg : messageMapper.findPending(100)) {
        try {
            inventoryClient.deduct(msg.getOrderId());   // 幂等（库存侧按 orderId 去重）
            messageMapper.updateStatus(msg.getId(), "SUCCESS");
        } catch (Exception e) {
            // 记录失败次数，超限告警人工介入
        }
    }
}
```

### 46.6.2 优缺点

- ✅ 实现简单、不依赖中间件特殊能力、可靠性有兜底（定时重扫）；
- ❌ 消息表与业务表耦合在同一个库（拆库失效）、重复消费需幂等、大量消息时扫表性能差。

> **一句话**：本地消息表是老牌"土方案"，思路（**事务内写消息 + 后台兜底重发**）是所有可靠消息的祖传思想——MQ 事务消息就是它的"中间件化"。

## 46.7 MQ 事务消息：RocketMQ 半消息机制

### 46.7.1 原理（把本地消息表搬进 MQ）

RocketMQ 的事务消息 = 本地消息表思想的中间件化，用**半消息（Half Message）**解决"业务操作与发消息的原子性"：

```
生产者（下单服务）：
  ① 发送半消息（half message）到 MQ（此时消费者不可见）
  ② 执行本地事务（写订单表）
  ③ 本地事务成功 ──► commit（半消息变为可见，消费者可消费）
    本地事务失败 ──► rollback（消息丢弃）
  ④ MQ 长时间没收到 commit/rollback ──► 反查本地事务状态（接口回查）
```

```java
// 事务监听器：本地事务 + 回查
@Component
public class OrderTransactionListener implements RocketMQLocalTransactionListener {

    @Override
    @Transactional
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            orderMapper.insert((Order) arg);
            return RocketMQLocalTransactionState.COMMIT;    // 本地成功，提交消息
        } catch (Exception e) {
            return RocketMQLocalTransactionState.ROLLBACK;  // 本地失败，丢弃消息
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        // MQ 回查：订单是否存在？存在→COMMIT，不存在→ROLLBACK
        String orderId = parseOrderId(msg);
        return orderMapper.exists(orderId)
                ? RocketMQLocalTransactionState.COMMIT
                : RocketMQLocalTransactionState.ROLLBACK;
    }
}
```

### 46.7.2 优缺点

- ✅ 本地事务与消息发送原子性由 MQ 保证（不依赖手动扫表）、吞吐高、解耦彻底；
- ❌ 依赖 RocketMQ（RabbitMQ/Kafka 无原生事务消息）、消费者需幂等、回查接口要严谨。

> **一句话**：RocketMQ 事务消息是"下单后异步通知"类场景的最优解，**配合第 43 章 Outbox 思想更佳**（两者本质都是"可靠投递"）。

## 46.8 Saga：长流程的最终一致（本章主角）

### 46.8.1 思想：正向执行 + 反向补偿

Saga 把长事务拆成**一串本地事务**，每个本地事务配一个**补偿事务（Compensation）**：

```
正常路径：T1(下单) → T2(扣库存) → T3(扣款) → 完成
失败路径：T2 失败 ──► 补偿 C1(取消订单) ──► 全部回滚（最终一致）
```

特点：
- **没有全局锁**，每个 T 提交即释放资源（性能远好于 2PC）；
- **补偿是业务行为**（取消订单、退款、解冻），不是数据库回滚；
- 中间态对外可见（订单可能短暂"已创建但库存未扣"），需要容忍最终一致。

### 46.8.2 两种编排方式（面试核心）

**① 协同式（Choreography）：事件驱动，无中心协调者**

```
下单服务 下单成功 ──发事件──► 库存服务 扣库存成功 ──发事件──► 账户服务 扣款成功
    ▲                                                          │
    │                         失败：库存服务发现库存不足          │
    └─────────────────────── 发"扣库存失败"事件 ────────────────┘
    下单服务 监听失败事件 ──► 取消订单（补偿）
```

- ✅ 无单点、服务完全解耦、天然事件驱动（呼应 43/45 章）；
- ❌ 流程隐式（要靠事件追踪）、循环依赖风险、难以排查"走到哪一步了"。

**② 编排式（Orchestration）：中心协调者（Saga Orchestrator）**

```
                    ┌────────────── Saga 协调者（状态机）──────────────┐
                    │  步骤1: 下单 ──► 步骤2: 扣库存 ──► 步骤3: 扣款      │
                    │    │              │ 失败↓             │             │
                    │    ▼              ▼                   ▼             │
                    │  补偿: 取消订单 ◄── 补偿: 解冻库存 ◄── 补偿: 退款     │
                    └──────────────────────────────────────────────────┘
```

- ✅ 流程显式可控（状态机化，直接接第 42 章 Spring StateMachine）、易调试、易监控；
- ❌ 协调者成为单点/耦合点（要部署高可用）。

> **选型**：步骤少、事件语义清晰用**协同式**；步骤多、补偿逻辑复杂用**编排式**（配合状态机）。第 45 章 Axon Saga 是协同式的框架实现；Seata Saga / 自研状态机是编排式。

### 46.8.3 与 TCC 的对比

| 维度 | TCC | Saga |
| --- | --- | --- |
| 一致性 | 强一致（Try 预留） | 最终一致 |
| 补偿方式 | Confirm/Cancel 业务方法 | 独立的补偿事务 |
| 中间态可见 | 预留态可见，但不影响正确性 | 中间态可见且可能被读到"未完成" |
| 侵入性 | 三方法 + 三坑 | 每步一个补偿方法 |
| 适用 | 资金、库存（要预留） | 长流程、弱一致业务（订单/物流/预订） |
| 恢复 | 需要控制表 | 需要 Saga 日志/状态机持久化 |

## 46.9 Seata：一站式分布式事务框架

### 46.9.1 四种模式

| Seata 模式 | 对应本章方案 | 侵入性 | 一句话 |
| --- | --- | --- | --- |
| **AT** | 自动补偿（近似 TCC） | 最低（无侵入） | 拦截 SQL 生成 undo_log，失败自动回滚 |
| **TCC** | TCC | 高 | 业务写三方法 |
| **Saga** | Saga 编排 | 中 | 状态机 JSON 定义流程+补偿 |
| **XA** | 2PC | 低但锁资源 | 数据库原生 XA |

**AT 模式原理（最常用）**：

```
① 全局事务开启：TC（Transaction Coordinator）注册
② 每个参与者本地事务执行 SQL 时：
     - 生成 before image（修改前快照）→ 执行 SQL → 生成 after image
     - 连同 undo_log 一起本地提交
③ 全局提交：删除 undo_log（异步）
④ 全局回滚：根据 undo_log 做反向 SQL（after→before），补偿回滚
```

```yaml
# Seata 客户端配置（application.yml）
seata:
  tx-service-group: mall_tx_group
  service:
    vgroup-mapping:
      mall_tx_group: default
  registry:
    type: nacos
```

```java
// 使用：一行注解，全局事务
@GlobalTransactional
public void createOrder(OrderDTO dto) {
    orderService.create(dto);        // 订单库
    inventoryService.deduct(dto);    // 库存库（Feign）
    accountService.pay(dto);         // 账户库（Feign）
    // 任一步失败：AT 自动回滚所有已提交的本地事务
}
```

> 第 32 章 mall-cloud 已经用过 Seata（`@GlobalTransactional`），本章把它的原理补齐：**AT = 数据库行级 undo_log 自动补偿**，读未提交期间数据短暂可见（全局锁控制写并发）。

### 46.9.2 Seata 适用判断

- AT 模式：存量项目无侵入快速接、业务可以容忍"全局锁带来的短暂写阻塞"；
- TCC 模式：资金强一致 + 团队愿意写三方法；
- Saga 模式：长流程 + 不想改业务 SQL。

## 46.10 实战：下单 Saga（编排式 + 状态机）

用第 42 章的 Spring StateMachine 实现 Saga 协调者，串起"下单 → 扣库存 → 扣款"三步，任一步失败走补偿。

### 46.10.1 状态与事件定义

```java
public enum SagaState { START, ORDER_CREATED, INVENTORY_DEDUCTED, COMPLETED, CANCELLED }

public enum SagaEvent { CREATE_ORDER_OK, DEDUCT_INVENTORY_OK, PAY_OK, STEP_FAILED, COMPENSATED }
```

### 46.10.2 协调者状态机（迁移表 = 业务宪法）

```java
@Configuration
public class OrderSagaStateMachineConfig {

    @Bean
    public StateMachineFactory<SagaState, SagaEvent> sagaMachineFactory(
            StateMachineBuilder.Builder<SagaState, SagaEvent> builder) {

        builder.configureStates()
               .withStates()
               .initial(SagaState.START)
               .state(SagaState.ORDER_CREATED)
               .state(SagaState.INVENTORY_DEDUCTED)
               .end(SagaState.COMPLETED)
               .end(SagaState.CANCELLED);

        builder.configureTransitions()
               // 正常流转
               .withExternal()
                   .source(START).target(ORDER_CREATED)
                   .event(CREATE_ORDER_OK).action(ctx -> orderService.create(ctx))
               .and()
               .withExternal()
                   .source(ORDER_CREATED).target(INVENTORY_DEDUCTED)
                   .event(DEDUCT_INVENTORY_OK).action(ctx -> inventoryService.deduct(ctx))
               .and()
               .withExternal()
                   .source(INVENTORY_DEDUCTED).target(COMPLETED)
                   .event(PAY_OK).action(ctx -> accountService.pay(ctx))
               // 任意一步失败 → CANCELLED（按失败步骤触发对应补偿）
               .and()
               .withExternal()
                   .source(ORDER_CREATED).target(CANCELLED)
                   .event(STEP_FAILED).action(ctx -> compensate(ctx, Step.INVENTORY))
               .and()
               .withExternal()
                   .source(INVENTORY_DEDUCTED).target(CANCELLED)
                   .event(STEP_FAILED).action(ctx -> compensate(ctx, Step.ACCOUNT));

        return builder.build();
    }

    // 补偿：按失败步骤回滚已完成的前序步骤
    private void compensate(StateContext<SagaState, SagaEvent> ctx, Step failedStep) {
        OrderSagaContext data = (OrderSagaContext) ctx.getExtendedState().getVariables().get("ctx");
        switch (failedStep) {
            case INVENTORY -> orderService.cancel(data.orderId());      // 补偿 T1：取消订单
            case ACCOUNT -> {
                inventoryService.unfreeze(data.skuId(), data.count());  // 补偿 T2：解冻库存
                orderService.cancel(data.orderId());                    // 补偿 T1：取消订单
            }
        }
    }
}
```

### 46.10.3 协调者服务（异步推进 + 超时）

```java
@Service
public class OrderSagaCoordinator {

    private final StateMachineFactory<SagaState, SagaEvent> factory;

    public void start(OrderSagaContext ctx) {
        StateMachine<SagaState, SagaEvent> sm = factory.getStateMachine(); // 每个订单一个实例
        sm.getExtendedState().getVariables().put("ctx", ctx);
        sm.sendEvent(SagaEvent.CREATE_ORDER_OK);   // 步骤1：下单
    }

    // 库存服务回调（异步事件/Feign）
    public void onInventoryDeducted(String orderId) {
        smOf(orderId).sendEvent(SagaEvent.DEDUCT_INVENTORY_OK);  // 步骤2 成功
    }

    // 超时/失败入口
    public void onStepFailed(String orderId) {
        smOf(orderId).sendEvent(SagaEvent.STEP_FAILED);          // 进入补偿
    }
}
```

### 46.10.4 补偿的可靠性三件套

| 机制 | 说明 |
| --- | --- |
| **幂等** | 补偿/正向操作按订单号去重（补偿可能重试） |
| **Saga 日志** | 记录每步状态（`saga_log` 表），失败后定时任务继续推进/补偿 |
| **对账兜底** | 每日对账任务扫描"卡在中间态"的 Saga 并人工/自动修复 |

> 补偿与第 43 章事件、第 45 章 Axon Saga 是同构思想：**正向流程驱动 + 失败事件触发反向流程**。

## 46.11 选型决策树

```
业务真的需要跨服务事务吗？
├─ 否（可合并聚合/可异步）──────► 不用任何分布式事务（砍需求）
└─ 是：
    ├─ 需要强一致吗？
    │   ├─ 是：
    │   │   ├─ 能接受阻塞、同构库 ──► 2PC/XA（银行核心）
    │   │   └─ 需要高并发 ──► TCC（资金/库存强扣减，Seata TCC）
    │   └─ 否（最终一致即可）：
    │       ├─ 纯"写后通知" ──► MQ 事务消息（RocketMQ）
    │       ├─ 已有 MQ 但无事务消息 ──► 本地消息表/Outbox（第 43 章）
    │       ├─ 长流程多步骤 ──► Saga
    │       │     ├─ 步骤少、事件清晰 ──► 协同式（Axon/自研事件）
    │       │     └─ 步骤多、补偿复杂 ──► 编排式（状态机/Seata Saga）
    │       └─ 存量项目快速接入 ──► Seata AT
```

**行业常见组合**：`支付/账务 → TCC 或 2PC` + `订单/库存/物流 → Saga/本地消息表` + `通知/积分 → MQ 事务消息`。原则：**能用最终一致就别用强一致，能砍事务就别上方案**。

## 46.12 练习与思考

1. **练习 A**：给下单流程（下单→扣库存→扣款）分别用 TCC 和 Saga 设计三方法/补偿方法，对比两者需要的字段与中间态；
2. **练习 B**：用 Spring StateMachine 实现 46.10 的 Saga 协调者，补上超时事件（30 秒未完成→STEP_FAILED）与 Saga 日志表；
3. **练习 C**：把第 32 章 mall-cloud 的 Seata `@GlobalTransactional` 换成"本地消息表"方案，对比代码与一致性差异；
4. **练习 D**：设计"下单+发券"场景，比较 RocketMQ 事务消息与本地消息表两种实现的可靠性与性能。

## 46.13 面试考点

1. 为什么微服务后本地事务失效？哪些"跨服务事务"其实是伪需求？
2. CAP 与 BASE 如何指导分布式事务选型？
3. 2PC 两阶段流程？三个致命缺点？
4. TCC 三阶段含义？三大坑（空回滚/悬挂/幂等）如何解决？
5. 本地消息表原理与优缺点？
6. RocketMQ 事务消息的 half message 与回查机制？
7. Saga 的编排式与协同式区别、优缺点、适用？
8. TCC 与 Saga 的核心区别？
9. Seata AT 模式原理（undo_log before/after image）？四种模式对比？
10. 下单场景选型：为什么通常用 Saga/MQ 而不是 2PC？

---

至此，第 46 章分布式事务与 Saga 学习完成。下一章 [47-企业级架构专题（上）-高并发与高可用.md](./47-企业级架构专题（上）-高并发与高可用.md)（把前面的"术"汇入企业级架构的"道"）｜ 返回：[README.md](./README.md)
