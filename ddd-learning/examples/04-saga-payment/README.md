# examples/04 - Saga 跨服务集成（订单-库存-支付）

> **定位**：演示 Saga 跨服务协调 + Outbox 模式 + 编排式状态机。
> **配套章节**：[第 79 章 跨上下文集成 Saga/Outbox](../../java-learning/79-跨上下文集成SagaOutbox.md)、[第 80 章 专题收官](../../java-learning/80-专题收官演进路线.md)

## 一、技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| 状态机 | Spring StateMachine 4.0 |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | H2 内存（可换 MySQL） |

## 二、运行步骤

```bash
mvn spring-boot:run
```

## 三、Saga 全流程

```
下单 ──► 库存预留 ──► 金额冻结 ──► [等待用户付款] ──► 扣款 ──► 库存扣减 ──► 完成
                                                                  │
                                                              （失败则补偿）
                                                                  │
                                                                  ▼
                                                          释放库存 / 解冻金额
```

## 四、Saga 状态机

```
┌──────┐  RESERVE   ┌──────┐  FREEZE   ┌──────┐  PAY   ┌──────┐
│START │ ────────► │RESVD │ ────────► │FROZEN│ ─────► │ PAID │
└──┬───┘            └──────┘            └──────┘        └──┬───┘
   │                                                      │ DEDUCT
   │ CANCEL                                               ▼
   ▼                                                  ┌──────┐ COMMIT ┌────────┐
CANCELLED                                             │DEDUCT│ ──────► │COMMITTED│ ──► COMPLETED
                                                      └──────┘         └────────┘
```

## 五、目录结构

```
com.ddd.saga/
├── DddSagaApplication.java
├── order/                    ← 订单上下文（精简）
│   ├── domain/Order.java
│   └── application/OrderAppService.java
├── inventory/                ← 库存上下文
│   ├── domain/Inventory.java
│   └── application/InventoryAppService.java
├── payment/                  ← 支付上下文
│   ├── domain/Account.java
│   └── application/PaymentAppService.java
├── saga/                     ← Saga 协调
│   ├── OrderSagaStateMachine.java   ← 状态机
│   └── OrderSagaCoordinator.java    ← 协调器
└── outbox/                   ← Outbox 模式
    ├── OutboxEvent.java
    ├── OutboxWriter.java          ← 业务事务内写
    └── OutboxScheduler.java       ← 后台扫描投递
```

## 六、关键设计

### 6.1 Outbox 模式

业务事务提交时，同步写 `outbox_event` 表。后台 `OutboxScheduler` 每 5 秒扫描 PENDING 状态事件投递到 MQ，失败重试（指数退避）+ 死信处理。

### 6.2 幂等性

每个支付操作携带 `idempotency_key`，应用层先查 → 已有则返回上次结果；DB 唯一约束保底防并发。

### 6.3 补偿

Saga 失败时反向触发：
- 库存释放（InventoryPort.release）
- 金额解冻（PaymentPort.unfreeze）

补偿操作必须幂等（基于 transactionId）。