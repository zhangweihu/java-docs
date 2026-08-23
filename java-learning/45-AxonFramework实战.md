# 第四十五章 Axon Framework 实战（CQRS + 事件溯源的工程化框架）

> 本章目标：掌握 Axon Framework——把第 43 章手写的 CQRS + 事件溯源工程化的成熟框架：理解四大总线（Command/Event/Query）与 Aggregate/Saga/Event Store/读模型等核心概念，用 Axon 完整实现"账户转账"（聚合、命令处理、事件投影、查询模型、Saga 编排），并学会判断"什么时候该用 Axon、什么时候别用"。
>
> 前置知识：第四十三章 CQRS 与事件溯源（Axon 就是它的工业实现）、第四十一章 DDD 战术设计（聚合/值对象）、第四十四章 事件风暴（建模输入）、Spring Boot 基础（第九、十章）。

## 45.1 Axon 是什么：把第 43 章的手写版工程化

### 45.1.1 从"手写 CQRS/ES"到"框架"

第 43 章我们手写了事件存储、聚合重放、投影器、快照——每一样都不难，但凑齐一套生产级设施很累：

| 手写版（第 43 章） | 生产级要求 | Axon 内置 |
| --- | --- | --- |
| 自己管理事件表 + 版本乐观锁 | 高并发、多实例并发写事件 | Event Store（并发控制内置） |
| 自己写重放逻辑 | 读模型重建、订阅重放 | Replay 机制（Tracking Processor） |
| 自己写投影器 | 幂等、并发处理、失败重试 | Event Processor（分片、错误处理） |
| 自己组装命令→事件→查询 | 消息分发、网关、调度 | 四大总线 + Gateway |
| 自己处理超时/编排（第 46 章 Saga） | 分布式编排、关联事件 | Saga 支持 |
| 自己写快照 | 按事件数定期快照 | Snapshot Trigger 策略 |

**Axon Framework** 是一个开源的 **CQRS + Event Sourcing + Saga** 微服务开发框架：你用普通 POJO + 注解声明"聚合怎么响应命令、事件怎么更新读模型"，框架负责消息分发、事件存储、重放、快照、Saga 生命周期。

> **一句话记忆**：Axon = 第 43 章全部手写代码 + 工程化（并发、重放、分片、Saga），用注解把"业务声明"和"基础设施"彻底解耦。

### 45.1.2 典型架构（一张图）

```
写侧（Command Side）                         读侧（Query Side）
┌──────────────────────────────┐           ┌──────────────────────────────┐
│  Client ──► CommandGateway     │           │  Client ──► QueryGateway      │
│    └──► CommandBus             │           │    └──► QueryBus              │
│          └──► CommandHandler   │           │          └──► @QueryHandler   │
│                └──► @Aggregate │           │               （读模型查询）    │
│                      │ apply() │           └──────────────────────────────┘
│                      ▼         │
│                EventBus ──┬───►│───► Event Handler（投影器/读模型）
│                           │    │           （Tracking/Segmented，可重放）
│                           ▼    │
│                 Event Store    │           ┌──────────────────────────────┐
│                 （事件溯源存储）  │           │  Saga（@Saga）跨聚合编排        │
└──────────────────────────────┘           └──────────────────────────────┘
```

## 45.2 核心概念速查（先背这 8 个）

| 概念 | 是什么 | 对应第 43 章手写版 |
| --- | --- | --- |
| **Command** | 写操作意图（`TransferMoneyCommand`），命令对象 | 命令对象 |
| **Event** | 已发生事实（`MoneyTransferredEvent`），过去式 | 领域事件 |
| **Query** | 读操作意图（`FindAccountBalanceQuery`） | 查询 DTO |
| **Aggregate** | 聚合根，命令的处理者，`apply()` 产出事件 | 聚合根 |
| **CommandBus / EventBus / QueryBus** | 三条消息通道，解耦发送方与处理方 | 手写无（自己调方法） |
| **CommandGateway / QueryGateway** | 客户端发消息的便捷门面（异步/同步） | 无 |
| **Event Processor** | 消费事件更新读模型，支持分片/重放/错误处理 | 手写投影器 |
| **Saga** | 跨聚合的流程编排（如转账→通知→超时） | 无（第 46 章手写） |

> **与 Spring 的类比**：CommandBus 之于 CommandHandler ≈ Spring 的 DispatcherServlet 之于 @Controller。你只写"处理器"，框架负责"路由"。

## 45.3 快速开始：工程骨架

### 45.3.1 依赖（Maven）

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.x</version>
</parent>

<dependencies>
    <!-- Axon 核心：总线、聚合、Event Store -->
    <dependency>
        <groupId>org.axonframework</groupId>
        <artifactId>axon-spring-boot-starter</artifactId>
        <version>4.9.x</version>
    </dependency>
    <!-- 事件存储持久化到 MySQL -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>
</dependencies>
```

`axon-spring-boot-starter` 自动配置：CommandBus、EventBus、QueryBus、Event Store、处理器线程池。默认 Event Store 用 **JPA 存到 MySQL 的 `domain_event_entry` 表**（一张表存所有聚合事件，字段含 `aggregate_identifier`、`sequence_number`、`event_type`、`payload`）。

### 45.3.2 四个核心配置项（application.yml）

```yaml
axon:
  serializer:
    general: jackson          # 事件/命令序列化（生产常用 Jackson）
    events: jackson           # 事件存储中的 payload 序列化
  eventhandling:
    processors:
      account-group:          # 自定义事件处理器组名（投影用）
        mode: tracking        # tracking：后台线程消费 + 支持重放；subscribing：同步
  snapshot:
    trigger:
      threshold: 100          # 每个聚合每 100 个事件自动触发快照
```

## 45.4 写侧：聚合（@Aggregate）

### 45.4.1 聚合 = 普通类 + 注解

```java
@Aggregate                       // 标记聚合根
public class Account {
    @AggregateIdentifier          // 聚合 id（事件存储按它分区）
    private String accountId;

    private BigDecimal balance;   // 余额（状态由事件重放而来）
    private boolean active;

    protected Account() {         // Axon 重放需要无参构造（框架用反射创建）
    }

    // ── 命令处理器：创建账户 ─────────────────────────────
    @CommandHandler
    public Account(CreateAccountCommand cmd) {
        // 校验：初始化余额必须 >= 0
        if (cmd.initialBalance().signum() < 0)
            throw new IllegalArgumentException("初始余额不能为负");
        apply(new AccountCreatedEvent(cmd.accountId(), cmd.owner(), cmd.initialBalance()));
    }

    // ── 命令处理器：转账（资金转出方）──────────────────────
    @CommandHandler
    public void handle(TransferMoneyCommand cmd) {
        // 校验规则（白便签 → 聚合内不变量，第 44 章）
        if (!active) throw new IllegalStateException("账户已冻结");
        if (balance.compareTo(cmd.amount()) < 0)
            throw new InsufficientBalanceException("余额不足");
        apply(new MoneyDebitedEvent(accountId, cmd.amount(), cmd.transferId()));
    }

    // ── 事件处理器：状态变更（唯一改状态的地方）──────────────
    @EventSourcingHandler
    public void on(AccountCreatedEvent evt) {
        this.accountId = evt.accountId();
        this.balance = evt.initialBalance();
        this.active = true;
    }

    @EventSourcingHandler
    public void on(MoneyDebitedEvent evt) {
        this.balance = this.balance.subtract(evt.amount());
    }

    @EventSourcingHandler
    public void on(MoneyCreditedEvent evt) {
        this.balance = this.balance.add(evt.amount());
    }
}
```

**关键机制（与第 43 章手写版一一对应）**：

| Axon 机制 | 对应手写版 |
| --- | --- |
| `@CommandHandler` 方法 | 聚合根的 `submit()/transfer()` 命令方法 |
| `apply(event)` | 手写版的 `apply(evt)`：先本地 `@EventSourcingHandler` 改内存状态 |
| `@EventSourcingHandler` | 手写版 `apply()` 里的状态变更 |
| `@AggregateIdentifier` | 手写版 `id` 字段 + 事件表 `aggregate_id` |

> **重放机制（为什么要有无参构造）**：命令处理前，Axon 从 Event Store 读出该聚合的**全部历史事件**，按顺序调用对应 `@EventSourcingHandler` 把状态"重放"出来——内存里的 balance 就是这么来的。所以**事件处理器是唯一改状态的入口**，命令里绝不直接 `this.balance = ...`。

### 45.4.2 命令入站：CommandHandler 的三种挂载方式

| 方式 | 写法 | 适用 |
| --- | --- | --- |
| 聚合内 `@CommandHandler` | 命令直接路由到聚合方法 | 命令目标明确是某个聚合 |
| 外部 `@CommandHandler` + `@AggregateLoad` | 先加载聚合再调用 | 需要自定义逻辑/读取其他数据 |
| `@CommandHandler`（无状态组件） | 处理事务脚本类命令 | 命令不绑定聚合 |

### 45.4.3 事件出站：命令返回的事件

`apply()` 发布的事件被框架持久化到 Event Store，并自动广播给所有 Event Processor。**命令与事件是同一个事务**（Axon 默认在命令处理事务内写事件）。

## 45.5 发送命令：CommandGateway

客户端（应用服务/Controller）通过 Gateway 发命令：

```java
@Service
public class AccountApplicationService {

    private final CommandGateway commandGateway;

    public AccountApplicationService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public String createAccount(String owner, BigDecimal initial) {
        return commandGateway.sendAndWait(
                new CreateAccountCommand(UUID.randomUUID().toString(), owner, initial));
    }

    public void transfer(String fromId, String toId, BigDecimal amount) {
        commandGateway.sendAndWait(new TransferMoneyCommand(fromId, toId, amount));
        // sendAndWait：同步等待结果；send()：异步（CompletableFuture）
    }
}
```

> **注意**：命令是"意图"对象（`TransferMoneyCommand`），**不是 API DTO**——Controller 收到 JSON 后转成命令对象再发出去，命令里可以带聚合 id、上下文信息。

## 45.6 读侧：事件投影（@EventHandler）与查询（@QueryHandler）

### 45.6.1 投影器：消费事件更新读模型

```java
// 读模型实体（JPA，对应第 43 章的读模型宽表）
@Entity
public class AccountView {
    @Id public String accountId;
    public String owner;
    public BigDecimal balance;
    public String status;
}

// 投影器：监听事件 → 更新读模型表
@Component
public class AccountProjector {

    @EventHandler  // 默认归属 processors: account-group（见 yml 配置）
    public void on(AccountCreatedEvent evt) {
        accountViewRepository.save(new AccountView(evt.accountId(), evt.owner(), evt.initialBalance(), "ACTIVE"));
    }

    @EventHandler
    public void on(MoneyDebitedEvent evt) {
        accountViewRepository.findById(evt.accountId())
            .ifPresent(v -> { v.balance = v.balance.subtract(evt.amount()); accountViewRepository.save(v); });
    }

    @EventHandler
    public void on(MoneyCreditedEvent evt) {
        accountViewRepository.findById(evt.accountId())
            .ifPresent(v -> { v.balance = v.balance.add(evt.amount()); accountViewRepository.save(v); });
    }
}
```

**投影器必须幂等**：Tracking Processor 消费失败会**重试**，同一事件可能被处理两次——更新类操作天然幂等，新增类要按 id 去重（`save` 前检查存在性）。

### 45.6.2 查询处理器：读模型查询

```java
// 查询对象
public record FindAccountBalanceQuery(String accountId) {}
public record AccountBalanceResult(String accountId, BigDecimal balance) {}

// 查询处理器（读模型 Repository 上直接查）
@Component
public class AccountQueryHandler {

    @QueryHandler
    public AccountBalanceResult handle(FindAccountBalanceQuery query) {
        return accountViewRepository.findById(query.accountId())
            .map(v -> new AccountBalanceResult(v.accountId, v.balance))
            .orElseThrow(() -> new AccountNotFoundException(query.accountId()));
    }
}

// 客户端发送查询
@Service
public class AccountQueryService {
    private final QueryGateway queryGateway;
    public AccountBalanceResult queryBalance(String accountId) {
        return queryGateway.query(new FindAccountBalanceQuery(accountId), AccountBalanceResult.class)
                           .join();
    }
}
```

### 45.6.3 读模型重建（Replay）

读模型表被误删/加字段需要重建时，**Tracking Processor 支持倒带重放**：

```powershell
# 重置读模型：Axon 会清空该处理器已处理的位置，从 0 号事件重新消费
POST /actuator/axon/event-processors/account-group/replay?reset-token=true
```

重放期间投影器会重复更新——再次验证**幂等设计**的重要性。

## 45.7 Saga：跨聚合流程编排

### 45.7.1 什么时候需要 Saga

转账涉及**两个聚合**（转出方扣款 + 转入方收款），第 43 章手写版把两步放一个事务（跨聚合强一致）。真实分布式环境拆成两个服务后，**不能一个本地事务搞定**——需要 Saga 编排（第 46 章详解）。Axon 内置 Saga 支持：

```java
@Saga                              // 标记 Saga
public class MoneyTransferSaga {

    @Autowired
    private transient CommandGateway commandGateway;   // 注入命令发送

    private String fromId;
    private String toId;
    private BigDecimal amount;

    @StartSaga                       // 该事件触发 Saga 开始
    @SagaEventHandler(associationProperty = "transferId")
    public void on(TransferStartedEvent evt) {
        this.fromId = evt.fromId();
        this.toId = evt.toId();
        this.amount = evt.amount();
        // 第一步：转出方扣款
        commandGateway.send(new TransferMoneyCommand(fromId, toId, amount, evt.transferId()));
    }

    @SagaEventHandler(associationProperty = "transferId")
    public void on(MoneyDebitedEvent evt) {
        // 第二步：转入方收款（关联到同一个 transferId）
        commandGateway.send(new CreditMoneyCommand(toId, amount, evt.transferId()));
    }

    @SagaEventHandler(associationProperty = "transferId")
    public void on(MoneyCreditedEvent evt) {
        // 全部完成：结束 Saga
        end();
    }

    @SagaEventHandler(associationProperty = "transferId")
    public void on(TransferFailedEvent evt) {
        // 失败：补偿（回滚扣款）
        commandGateway.send(new ReverseDebitCommand(fromId, amount, evt.transferId()));
        end();
    }
}
```

**Saga 三要点**：
1. **关联属性（associationProperty）**：事件按 `transferId` 路由到正确的 Saga 实例（框架存关联关系，类似第 46 章的协调表）；
2. **有状态**：Saga 实例是长期存活的对象（默认存内存/可选 JPA 持久化），事件驱动它一步步推进；
3. **补偿**：任何一步失败，通过发"撤销命令"回滚已完成步骤（第 46 章 Saga 的补偿逻辑，Axon 帮你管理生命周期）。

## 45.8 持久化与快照

### 45.8.1 Event Store（事件即唯一事实源）

- 默认 JPA 实现存 MySQL：`domain_event_entry`（事件）、`snapshot_event_entry`（快照）、`saga_entry`（Saga 状态）；
- 并发控制：每个聚合的事件按 `aggregate_id + sequence_number` 唯一约束，**重复写同一序列号直接冲突**（对应第 43 章手写版乐观锁）；
- 也支持 `axon-server`（专用事件存储服务，带查询/审计 UI）或 Mongo/Kafka。

### 45.8.2 快照

配置 `axon.snapshot.trigger.threshold: 100` 后，每 100 个事件自动生成快照（存 `snapshot_event_entry`）。重放时直接从最新快照开始，而不是从 0 重放全部——第 43 章手写快照，这里一行配置。

## 45.9 完整实战：账户转账（代码全景）

### 45.9.1 命令/事件定义（消息对象：过去式事件、祈使句命令）

```java
// 命令（写意图）
public record CreateAccountCommand(String accountId, String owner, BigDecimal initialBalance) {}
public record TransferMoneyCommand(String fromId, String toId, BigDecimal amount, String transferId) {}
public record CreditMoneyCommand(String accountId, BigDecimal amount, String transferId) {}
public record ReverseDebitCommand(String accountId, BigDecimal amount, String transferId) {}

// 事件（已发生事实，过去式）
public record AccountCreatedEvent(String accountId, String owner, BigDecimal initialBalance) {}
public record TransferStartedEvent(String transferId, String fromId, String toId, BigDecimal amount) {}
public record MoneyDebitedEvent(String accountId, BigDecimal amount, String transferId) {}
public record MoneyCreditedEvent(String accountId, BigDecimal amount, String transferId) {}
public record TransferFailedEvent(String transferId, String reason) {}
```

### 45.9.2 聚合（转账双方）

转出方 Account 聚合处理 `TransferMoneyCommand`（见 45.4.1），转入方聚合处理 `CreditMoneyCommand`：

```java
@Aggregate
public class Account {
    @AggregateIdentifier private String accountId;
    private BigDecimal balance;
    private boolean active;

    // ...AccountCreatedEvent 处理器同上...

    // 转入方：收款命令
    @CommandHandler
    public void handle(CreditMoneyCommand cmd) {
        if (!active) throw new IllegalStateException("账户已冻结");
        apply(new MoneyCreditedEvent(cmd.accountId(), cmd.amount(), cmd.transferId()));
    }

    // 补偿：撤销扣款
    @CommandHandler
    public void handle(ReverseDebitCommand cmd) {
        apply(new MoneyDebitedEvent(cmd.accountId(), cmd.amount().negate(), cmd.transferId()));
    }

    @EventSourcingHandler
    public void on(MoneyCreditedEvent evt) { this.balance = this.balance.add(evt.amount()); }
}
```

### 45.9.3 事务脚本（Saga 的起点，无状态组件）

转账发起入口——不绑定聚合，只负责"开启流程"：

```java
@Component
public class TransferCoordinator {

    private final CommandGateway commandGateway;

    @CommandHandler
    public void handle(StartTransferCommand cmd) {
        // 开启 Saga 的起始事件（Saga 关联 transferId 开始编排）
        commandGateway.send(new TransferStartedEvent(cmd.transferId(), cmd.fromId(), cmd.toId(), cmd.amount()));
    }
}
```

### 45.9.4 完整调用链

```
POST /api/transfer
  └─► StartTransferCommand ──► TransferCoordinator ──► TransferStartedEvent
        └─► MoneyTransferSaga（45.7）
              ├─► TransferMoneyCommand ──► Account(from) 扣款 ──► MoneyDebitedEvent
              ├─► CreditMoneyCommand ──► Account(to) 收款 ──► MoneyCreditedEvent ──► Saga end
              └─► 任一步异常 ──► TransferFailedEvent ──► ReverseDebitCommand 补偿
读模型：AccountProjector 同步更新 AccountView（余额查询走 QueryGateway）
```

**与第 43 章手写版对比**：同样实现账户转账，手写版约 200 行基础设施代码（事件存储、重放、投影、并发），Axon 版约 60 行业务代码 + 注解，基础设施全托管。

## 45.10 Axon 的优缺点与选型判断

### 45.10.1 优点

- **业务/基础设施彻底解耦**：聚合、投影、Saga 都是普通 POJO + 注解，换存储、换序列化不影响业务代码；
- **工程能力内置**：并发事件写、重放、分片、快照、错误重试、幂等指引——都是生产踩坑后的成熟方案；
- **Saga 一等公民**：跨聚合编排无需自研（第 46 章会看到手写 Saga 有多繁琐）；
- **读模型自由**：查询侧可任意使用 JPA/ES/宽表，读写完全独立扩展。

### 45.10.2 缺点与代价

- **学习曲线陡**：命令/事件/查询/Saga/Processor 概念多，初学容易"会用注解但不懂机制"；
- **事件存储即单一事实源**：`domain_event_entry` 表成为性能与可用性关键点，需要像数据库一样运维（分片、备份）；
- **最终一致性负担**：读模型延迟可见，部分业务（余额强一致展示）要额外设计；
- **重放/迁移成本**：事件 schema 演进（第 43 章 43.5 版本兼容）必须严格纪律，改事件类型要双版本共存；
- **团队要求高**：没有 DDD 基础直接上 Axon 大概率翻车（先学 40~44 章）。

### 45.10.3 选型决策树

```
业务需要吗？
├─ 纯 CRUD、无复杂业务规则 ──► 不要 Axon（Spring Data JPA 即可）
├─ 领域规则复杂但读简单 ──► 充血模型 + 普通事务即可（第 40 章）
├─ 规则复杂 + 需要审计/回放/完整历史 ──► 考虑 Event Sourcing
│     └─ 团队熟悉 DDD、规模值得 ──► Axon Framework
│     └─ 只用 ES 不用 Saga ──► 可考虑 Eventuate/自研（第 43 章）
└─ 读模型复杂、需要独立扩展 ──► CQRS（可先只做读模型分离，Axon 可选）
```

## 45.11 练习与思考

1. **练习 A**：把第 43 章手写账户转账改为 Axon 版（聚合/命令/事件/投影/查询），对比代码量与职责划分；
2. **练习 B**：给转账增加"余额不足抛 `InsufficientBalanceException`"的测试，验证命令失败时**不产生事件**；
3. **练习 C**：给 Saga 增加 30 秒超时（`@SagaEventHandler` + 定时器），超时触发补偿，模拟第 42 章的超时思想；
4. **练习 D**：验证投影幂等——让同一事件处理两次（模拟重放），读模型结果应一致。

## 45.12 面试考点

1. Axon 与手写 CQRS/ES 的区别？哪些工程能力是内置的？
2. 四大总线与两个 Gateway 各负责什么？
3. `@CommandHandler` 与 `@EventSourcingHandler` 的区别？为什么命令里不能直接改状态？
4. 聚合重放的机制？为什么需要无参构造？
5. 投影器为什么必须幂等？Tracking Processor 重试会怎样？
6. Saga 的关联属性（associationProperty）解决什么问题？补偿如何触发？
7. Event Store 并发控制如何保证（aggregate_id + sequence_number 唯一约束）？
8. 快照的作用与触发方式？什么时候该调大/调小阈值？
9. Axon 的选型决策：什么项目该用、什么项目不该用？
10. 最终一致性带来的问题（读模型延迟）如何缓解？

---

至此，第 45 章 Axon Framework 实战学习完成。下一章 [46-分布式事务与Saga.md](./46-分布式事务与Saga.md)（深挖 Saga 背后的分布式事务全景与 Seata）｜ 返回：[README.md](./README.md)
