# 第四十三章 CQRS 与事件溯源（命令查询分离与事件流架构）

> 本章目标：理解 CQRS（Command Query Responsibility Segregation，命令查询职责分离）的核心思想与演进形态，掌握事件溯源（Event Sourcing）的本质——"事件即事实、状态可重放"，能独立实现"账户转账"的 CQRS + ES 完整示例（事件存储、聚合重放、快照、投影），理解最终一致性与读模型构建，并能做出正确的选型判断（什么时候该用、什么时候别用）。
>
> 前置知识：第四十一章 DDD 战术设计（聚合、仓储是 CQRS/ES 的建模基础）、第四十二章 Spring StateMachine（状态流转机制）、第三十三章 MySQL（事务与存储）、第十六章 RabbitMQ（事件总线）、第二十四章 JVM（性能考量）。

## 43.1 CQRS：把"读"和"写"拆开

### 43.1.1 一个被忽略的事实：读和写不是一回事

传统 CRUD 里，同一个领域模型既要管"写"（下单、支付、改状态），又要管"读"（各种列表、报表、看板、聚合查询）。问题：

| 场景 | 写的需求 | 读的需求 |
| --- | --- | --- |
| 订单系统 | 下单/支付/取消（事务、一致性、状态机） | 订单列表/销售报表/用户维度聚合查询（快、灵活） |
| 电商商品 | 上架/改价（少量、低频、一致性敏感） | 首页推荐/搜索/多维筛选（海量、高频、复杂） |
| 银行账户 | 存取款（强一致、防并发） | 流水查询/余额曲线/对账单（只读、大查询） |

**核心矛盾**：一个"为写优化的模型"（领域对象、状态机、事务）通常**不适合**"为读优化的查询"（宽表、视图、ES 索引、统计）。把两者硬绑在一个模型上，导致两边都别扭。

### 43.1.2 CQRS 的定义

**CQRS**：把系统的"命令（Command，写）"和"查询（Query，读）"在**模型层彻底分离**：

```
┌──────────────────────────── 传统 CUD（读写同模型） ────────────────────────────┐
│  Client ──► OrderService（读+写混在一起） ──► Order 实体 ──► t_order 表          │
└────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────── CQRS（读写分离模型） ──────────────────────────────┐
│  写侧（Command Side）                         读侧（Query Side）               │
│  Client ──► Command：下单/支付 ──► 领域模型/聚合 ──► 事件 ──► 投影(Projection)   │
│                                        │                    │                  │
│                                    写存储(事件存储/业务表)     ▼                │
│                                                    读模型(宽表/ES/物化视图)     │
│                                              Client ──► Query：查列表/报表     │
└────────────────────────────────────────────────────────────────────────────────┘
```

> **一句话记忆**：写侧用"**领域语言**"（下单、支付），读侧用"**查询语言**"（筛选、聚合、统计）。它们可以有各自的数据存储、各自的模型、各自的扩展策略。

### 43.1.3 CQRS 的三个演进形态（别一上来就全套）

| 形态 | 做法 | 复杂度 | 适用 |
| --- | --- | --- | --- |
| **形态 1：单库 CQRS** | 读写同一库，只是 Service 拆成 CommandService / QueryService，模型分层 | 低 | 大多数项目的正确起点 |
| **形态 2：读写分库** | 主库写 + 从库/ES 读，异步同步（消息/同步器） | 中 | 读多写少、查询重的系统 |
| **形态 3：独立读模型** | 读侧用宽表/物化视图/ES 完全重构，写侧用 DDD 聚合 | 高 | 复杂聚合查询、报表看板、搜索 |

> **重要忠告**：**80% 的"需要 CQRS"的项目，做到形态 1 就够了**。形态 2/3 带来最终一致性、同步延迟、数据对账等一堆新问题，没有真实痛点别上。

## 43.2 事件溯源（Event Sourcing）的本质

### 43.2.1 传统 CRUD 存的是"结果"，事件溯源存的是"过程"

**传统方式**（状态即事实）：账户余额被扣了 100 元，数据库里 `balance` 从 1000 改成 900——**中间过程丢失**，只有最终状态。

**事件溯源**（事件即事实）：余额变化不是"改字段"，而是**追加一条不可变事件**：

```
账户 1001 的事件流：
[1] AccountOpened    初始金额 1000      @ 2026-01-01 10:00
[2] MoneyDeposited   存入 500           @ 2026-01-03 09:12
[3] MoneyWithdrawn   取出 200           @ 2026-01-05 14:30
[4] MoneyTransferred 转出 100 → 1002    @ 2026-01-08 16:40
        ↓ 重放(Replay)
当前余额 = 1000 + 500 - 200 - 100 = 1200   ← 状态由事件流推导而来
```

**核心规则**：
1. **事件只追加（Append-only）**：永不修改、永不删除历史事件（改错了追加"冲正事件"）；
2. **事件是不可变事实**：`MoneyWithdrawn(100)` 发生就是发生了，谁都不能抹掉；
3. **状态是派生物**：当前状态 = 重放事件流计算得出（可缓存/快照加速）；
4. **事件是系统的唯一事实源（Source of Truth）**：业务表、ES、报表、统计都是"投影"（Projection），可从事件流随时重建。

### 43.2.2 事件溯源 vs 传统存储

| 对比 | 传统 CRUD | 事件溯源 |
| --- | --- | --- |
| 存储内容 | 最终状态（余额 1200） | 事件流（5 条事件） |
| 历史追溯 | ❌ 无法知道"为什么变成 1200" | ✅ 完整审计：每一笔怎么来的 |
| 修改历史 | 可能被 UPDATE 抹掉 | 物理不可能（append-only） |
| 重建状态 | 备份恢复 | 重放事件流（任意时点快照） |
| 查询现状 | 直接查当前值 | 需重放/快照+重放 |
| 存储量 | 小（一行） | 大（事件会无限增长，需快照/归档） |
| 复杂度 | 低 | 高（事件模型、版本兼容、重放） |

### 43.2.3 事件溯源 vs 数据库事务日志/CDC

| 对比 | 事件溯源 | MySQL binlog / CDC（如 Canal） |
| --- | --- | --- |
| 本质 | **业务事件**（领域语言，人可读） | 数据变更日志（SQL 级，面向技术） |
| 建模 | 由领域驱动，事件即业务 | 由数据库驱动，捕获变更 |
| 消费 | 业务系统直接消费 | 通常需转换层 |
| 关系 | 两者可共存（ES 事件 → 投影 → 落库，CDC 再把落库变更同步到 ES/缓存） | |

> 业界常见组合：**ES 作为写侧事实源 → 投影出读模型库 → CDC 把读模型库同步到 Elasticsearch**。各层各司其职。

## 43.3 CQRS + 事件溯源：完整架构

### 43.3.1 为什么 CQRS 和 ES 常被放在一起

- **写侧**：命令 → 聚合根执行行为 → **产生事件 → 追加到事件存储**（不直接改业务表）；
- **读侧**：订阅事件流 → **投影（Projection）成读模型**（宽表/ES/统计表），查询走读模型；
- 两者通过**事件总线**（RabbitMQ/Kafka/本地事件）解耦，天然形成**最终一致性**。

```
命令(写)侧                                  事件流                          读(查询)侧
┌────────────┐   命令    ┌───────────┐  事件追加  ┌──────────────┐  订阅  ┌─────────────────┐
│ Controller  │ ────────► │CommandBus  │ ────────► │ 事件存储(ES)  │ ─────► │ 投影器 Projector  │
└────────────┘           └───────────┘           └──────────────┘        └────────┬────────┘
                                │                  │      ▲                       │
                          ┌─────▼─────┐  重放/快照  │      │            ┌─────────▼─────────┐
                          │ 聚合根     │            │      │            │ 读模型(宽表/ES)     │
                          │ (Account)  │ ──────────┘      │            │ 查询服务 QueryService│
                          └───────────┘   更新状态快照    │            └─────────┬─────────┘
                                                          │                      │
                                                   领域事件(OrderPaid等)           │
                                                           │                      │
                                          ┌────────────────▼─────┐    ┌──────────▼──────────┐
                                          │ 事件总线(消息队列)     │    │ Controller(读接口)    │
                                          └──────────────────────┘    └─────────────────────┘
```

### 43.3.2 关键概念一览

| 概念 | 说明 | 类比 |
| --- | --- | --- |
| 命令（Command） | 写意图（`WithdrawMoney(100)`），命名用动词 | 请求"去做" |
| 事件（Event） | 已发生的事实（`MoneyWithdrawn(100)`），命名用过去式 | 记录"已做" |
| 事件存储（Event Store） | append-only 存储 + 按聚合查询事件流 | 数据库（写侧） |
| 聚合根（Aggregate） | 通过重放事件流恢复，执行行为产生新事件 | 领域模型（41 章） |
| 快照（Snapshot） | 定期保存状态，加速重放（避免全量重放） | 状态缓存 |
| 投影（Projection） | 消费事件构建读模型 | 读侧物化视图 |
| 最终一致性 | 命令成功≠读模型立即可见，异步同步 | 弱一致性权衡 |

## 43.4 实战：账户转账的 CQRS + ES 实现（手写最小版）

> 目标：不依赖框架，用最少的代码把"事件溯源 + 重放 + 投影"讲透，你就能看懂 Axon/Eventuate 等框架在干什么。

### 43.4.1 事件定义（不可变事实）

```java
// 事件基类
public abstract class DomainEvent {
    public final String aggregateId;   // 所属聚合
    public final long version;         // 版本号（防并发）
    public final Instant occurredAt;   // 发生时间
    public DomainEvent(String aggregateId, long version, Instant occurredAt) { ... }
}

// 具体事件（全部用过去式命名）
public class AccountOpened extends DomainEvent {
    public final BigDecimal initialBalance;
    public AccountOpened(String id, long version, BigDecimal initialBalance, Instant t) {
        super(id, version, t); this.initialBalance = initialBalance;
    }
}

public class MoneyDeposited extends DomainEvent {
    public final BigDecimal amount;
    public MoneyDeposited(String id, long version, BigDecimal amount, Instant t) {
        super(id, version, t); this.amount = amount;
    }
}

public class MoneyWithdrawn extends DomainEvent {
    public final BigDecimal amount;
    public MoneyWithdrawn(String id, long version, BigDecimal amount, Instant t) {
        super(id, version, t); this.amount = amount;
    }
}
```

### 43.4.2 聚合根：重放恢复状态 + 行为产生事件

```java
// 聚合根：状态从事件流重放而来，行为产生新事件（而不是直接改字段！）
public class Account {
    private String id;
    private BigDecimal balance = BigDecimal.ZERO;
    private long version = 0;

    // ── 构造：新账户产生"开户事件" ──
    public static Account open(String id, BigDecimal initial) {
        Account a = new Account();
        a.apply(new AccountOpened(id, 1, initial, Instant.now()));   // 应用事件改变状态
        return a;
    }

    // ── 行为：校验 + 产生事件（不直接改 balance！） ──
    public DomainEvent deposit(BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("存款必须为正");
        MoneyDeposited evt = new MoneyDeposited(id, version + 1, amount, Instant.now());
        apply(evt);              // 应用事件：内部改状态（内存）
        return evt;              // 返回事件给调用方（落事件存储 + 发布）
    }

    public DomainEvent withdraw(BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("取款必须为正");
        if (balance.compareTo(amount) < 0) throw new IllegalStateException("余额不足");
        MoneyWithdrawn evt = new MoneyWithdrawn(id, version + 1, amount, Instant.now());
        apply(evt);
        return evt;
    }

    // ── 应用事件：事件 -> 状态（唯一的"状态变更点"） ──
    public void apply(DomainEvent evt) {
        if (evt instanceof AccountOpened e) { balance = e.initialBalance; }
        else if (evt instanceof MoneyDeposited e) { balance = balance.add(e.amount); }
        else if (evt instanceof MoneyWithdrawn e) { balance = balance.subtract(e.amount); }
        version = evt.version;    // 版本推进
    }

    // ── 重放：从事件流恢复状态 ──
    public static Account replay(String id, List<DomainEvent> events) {
        Account a = new Account();
        a.id = id;
        events.forEach(a::apply);   // 按顺序应用所有事件 = 恢复完整状态
        return a;
    }

    public BigDecimal balance() { return balance; }
    public long version() { return version; }
}
```

> **理解关键**：`deposit/withdraw` 里**没有直接写 `balance` 字段的业务逻辑之外的东西**——状态变更全部收敛在 `apply(event)`，而 `apply` 是"事件 → 状态"的唯一映射。重放（replay）就是按顺序调用 N 次 `apply`。**同一批事件，重放多少遍，状态永远一致**——这就是事件溯源的确定性。

### 43.4.3 事件存储（append-only + 并发控制）

```java
@Repository
public class EventStore {
    private final EventMapper eventMapper;   // 表：t_domain_event(event_id, aggregate_id, event_type, version, payload, occurred_at)

    @Transactional
    public void append(String aggregateId, DomainEvent event) {
        // 乐观锁：version 必须是 当前版本+1，防止并发下两个命令同时改同一聚合
        int rows = eventMapper.insertIfVersion(
                aggregateId, event.version,
                serialize(event), event.occurredAt);
        if (rows == 0) throw new ConcurrentModificationException("并发冲突：" + aggregateId);
    }

    public List<DomainEvent> load(String aggregateId) {
        return eventMapper.selectByAggregateId(aggregateId).stream()
                .map(this::deserialize)        // JSON 反序列化回事件对象
                .toList();
    }

    public List<DomainEvent> loadFrom(long globalSequence) {  // 投影器增量消费用
        return eventMapper.selectAfterSequence(globalSequence);
    }
    // serialize/deserialize：Jackson 按 eventType 多态序列化（省略）
}
```

### 43.4.4 命令侧应用服务（写）：转账

```java
@Service
public class AccountCommandService {
    private final EventStore eventStore;
    private final ApplicationEventPublisher publisher;   // 本地事件（或发 MQ）

    @Transactional
    public void transfer(String fromId, String toId, BigDecimal amount) {
        // 1. 重放恢复两个聚合
        Account from = Account.replay(fromId, eventStore.load(fromId));
        Account to = Account.replay(toId, eventStore.load(toId));

        // 2. 执行行为，得到事件
        DomainEvent withdrawn = from.withdraw(amount);    // 余额不足会抛异常（事务回滚）
        DomainEvent deposited = to.deposit(amount);

        // 3. 事件落库（append-only，两笔都成功才提交）
        eventStore.append(fromId, withdrawn);
        eventStore.append(toId, deposited);

        // 4. 发布事件给投影器/下游
        publisher.publishEvent(withdrawn);
        publisher.publishEvent(deposited);
    }
}
```

### 43.4.5 读侧投影（Projection）：构建读模型

```java
// 投影器：订阅事件，把读模型（账户余额表）更新成"重放结果的物化视图"
@Component
public class AccountProjector {
    @EventListener  // 实际生产走 MQ 异步订阅（43.4.6）
    public void on(MoneyWithdrawn e) {
        accountReadRepo.updateBalance(e.aggregateId, b -> b.subtract(e.amount));
    }
    @EventListener
    public void on(MoneyDeposited e) {
        accountReadRepo.updateBalance(e.aggregateId, b -> b.add(e.amount));
    }
    @EventListener
    public void on(AccountOpened e) {
        accountReadRepo.insert(new AccountReadModel(e.aggregateId, e.initialBalance));
    }
}

// 读侧查询：走"读模型表"，不做领域重放（快、独立扩展）
@RestController
public class AccountQueryController {
    @GetMapping("/accounts/{id}/balance")
    public BigDecimal balance(@PathVariable String id) {
        return accountReadRepo.findBalance(id);   // 直接查读模型表
    }
}
```

### 43.4.6 快照与增量投影（应对事件无限增长）

```java
// 快照：每 100 个事件存一次状态快照，重放时从最近快照开始而不是从 0 开始
@Repository
public class AccountSnapshotStore {
    public void snapshotIfNeeded(String id, Account account, long eventCount) {
        if (eventCount % 100 == 0) {
            snapshotMapper.upsert(new Snapshot(id, account.balance(), account.version(), Instant.now()));
        }
    }
}

// 重放优化：先取快照，再重放快照之后的事件
Account account = snapshotMapper.findLatest(id)
        .map(s -> Account.replay(id, eventStore.loadAfter(id, s.version())))
        .orElseGet(() -> Account.replay(id, eventStore.load(id)));
```

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| 全量重放 | 实现简单 | 事件多时慢（10 万事件重放一遍） |
| 快照 + 增量重放 | 速度快 | 快照存储与一致性维护 |
| 投影器维护读模型 | 查询极快 | 投影逻辑需幂等（事件可能重复投递） |

## 43.5 CQRS/ES 的工程细节

### 43.5.1 事件版本兼容（线上必考）

事件结构会变，但历史事件不能改。演进策略：

| 策略 | 做法 | 适用 |
| --- | --- | --- |
| 新增字段 + 默认值 | 事件类加字段，反序列化时缺失给默认值 | 最常用 |
| 版本号 + 升级器 | 事件带 `schemaVersion`，升级器把旧版事件转新版 | 结构大改 |
| 新事件类型 | 行为改了直接发新事件类型，旧事件原样保留 | 语义变化 |

```java
// 新增字段的事件：老数据反序列化时用默认值（Jackson @JsonSetter(nulls = NULLS_DEFAULT)）
public class MoneyDeposited extends DomainEvent {
    public final BigDecimal amount;
    public final String channel;                    // 新增字段：渠道（老事件为 null）
    public MoneyDeposited(String id, long version, BigDecimal amount, String channel, Instant t) { ... }
}
```

### 43.5.2 最终一致性的代价（必须让业务方知道）

| 场景 | 后果 |
| --- | --- |
| 用户下单后立即查"我的订单" | 可能看不到最新订单（投影还没跑完） |
| 支付成功刷新余额 | 余额可能短暂未更新 |
| 报表统计 | 与实时业务存在延迟 |

**缓解手段**：① 关键路径读模型与写模型同步更新（写后立即更新读模型）；② 前端轮询/提示"稍后刷新"；③ 读模型更新失败重试 + 对账补偿（用第 16 章 RabbitMQ 可靠投递 / 第 27 章 Kafka）。

### 43.5.3 事件与命令的命名规范

```
命令（将来时/动词）：WithdrawMoneyCommand、CreateOrderCommand、PayOrderCommand
事件（过去式/名词化）：MoneyWithdrawn、OrderCreated、OrderPaid、OrderCancelled
```

## 43.6 CQRS/ES 的选型：什么时候用，什么时候别用

### 43.6.1 适用场景与反模式

| 适合用 ✅ | 不适合用 ❌ |
| --- | --- |
| 需要**完整审计追溯**（金融、合规、医疗） | 简单 CRUD（增删改查，没历史价值） |
| 需要**任意时点重建状态**（反欺诈、对账） | 读模型与写模型几乎相同（读侧没增值） |
| **读多写少 + 复杂查询**（报表/看板/搜索） | 团队没有 DDD/事件建模经验 |
| 需要**时间旅行/回溯调试** | 对"立即读到自己写的数据"有硬性要求 |
| 复杂领域 + 事件驱动生态（Axon/Eventuate） | 事件量巨大且无归档方案 |

### 43.6.2 决策树

```
需要审计/回溯/合规吗？
├── 是 → ES（事件溯源）值得考虑
└── 否 ↓
读模型和写模型差异大吗（复杂查询/报表/搜索）？
├── 是 → CQRS（至少形态 1/2）
└── 否 → 传统 CRUD + DDD 聚合就够（别过度设计！）
```

### 43.6.3 常见框架（了解即可）

| 框架 | 定位 | 特点 |
| --- | --- | --- |
| **Axon Framework** | 国内知名 CQRS/ES 全家桶 | 命令总线、事件总线、事件存储、Saga，与 Spring 集成好 |
| **Eventuate Tram** | 微服务事件驱动 | 事务发件箱（Transactional Outbox）等模式 |
| **Spring Cloud Stream / Kafka** | 事件投递层 | 只做传输，不做聚合重放 |
| **Debezium / Canal** | CDC（变更数据捕获） | 从数据库捕获变更事件，反推式 ES |

> **事务发件箱模式（Outbox）**：跨"业务表事务"和"发事件"的一致性经典解法——业务变更和事件**写在同一张 outbox 表的一个事务里**，后台任务把 outbox 事件发到 MQ。ES 的 `append` 与发事件天然同事务，正是它相对"改表+发 MQ"的天然优势。

## 43.7 常见坑与最佳实践

### 43.7.1 高频坑

| 坑 | 表现 | 规避 |
| --- | --- | --- |
| **为 CQRS 而 CQRS** | 简单 CRUD 硬拆读写，白白引入最终一致性问题 | 按 43.6.2 决策树，80% 项目形态 1 就够 |
| **事件不可变被破坏** | 用 UPDATE 修改历史事件 | append-only 铁律；改错发冲正事件 |
| **事件类型被改名/删除** | 老事件反序列化失败，重放崩溃 | 事件类永久保留 + 版本兼容（43.5.1） |
| **重放非确定性** | apply 里读当前时间/查库，重放结果不一致 | apply 只依赖事件自身数据，纯函数化 |
| **并发双写事件流** | 两个命令同时 append 同一聚合 | 版本号乐观锁（43.4.3 insertIfVersion） |
| **投影不幂等** | MQ 重复投递导致读模型重复扣款 | 投影器按事件唯一 id 去重（消费端幂等） |
| **事件无限增长** | 重放越来越慢、存储膨胀 | 快照（43.4.6）+ 归档冷存储 |
| **把 ES 当万能** | 认为 ES 能替代数据库 | ES 是写侧事实源；读侧查询仍需要投影读模型 |
| **事务边界混乱** | append 事件成功但业务后处理失败 | 事件落库与业务副作用放同一事务；发 MQ 用 outbox |

### 43.7.2 最佳实践 Checklist

```
□ 先画"事件风暴"：领域专家口述业务，产出事件清单（过去式命名）
□ 命令进、事件出：聚合行为返回事件，不改外部状态
□ 事件 append-only + 版本乐观锁 + 幂等消费
□ 事件类一旦发布不再修改（新增字段给默认值；大改建新类型）
□ 快照策略：按聚合事件数（如 100）定期快照
□ 投影器消费事件构建读模型，读模型按查询优化（宽表/ES）
□ 关键路径"写后立即同步读模型"，缓解最终一致性体验问题
□ 对账任务定期比对：事件流总和 vs 读模型，发现不一致重放修复
□ 没有审计/回溯/复杂查询需求 → 不要上 ES（诚实评估）
```

## 43.8 练习与总结

### 练习题

```java
// 1. 用 43.4 的代码实现"账户存取款"：开户→存→取→查余额，断言余额=重放结果
// 2. 增加"转账"事件 MoneyTransferred，实现 transfer(from, to, amount)，
//    验证余额不足时整个事务回滚（from 和 to 都无变化）
// 3. 实现快照：每 3 个事件存一次快照，重放时从快照恢复并断言结果一致
// 4. 写一个投影器把账户事件投影成"月度流水报表表"，消费事件增量更新
// 5. 模拟并发：两个线程同时对同一账户存款（版本乐观锁），断言只有一个成功
// 6. 事件兼容演练：给 MoneyWithdrawn 加 channel 字段，老事件数据反序列化后 channel=null，
//    验证重放不报错（衔接 43.5.1）
// 7. （进阶）阅读 Axon Framework 官方 quickstart，对照本章手写代码理解框架封装了什么
```

### 面试高频问题清单

| 问题 | 答案要点 |
| --- | --- |
| 什么是 CQRS？ | 命令（写）与查询（读）模型分离，各自优化、可各自扩展存储 |
| CQRS 三个形态？ | 单库分层 / 读写分库 / 独立读模型（宽表/ES）；80% 项目形态 1 够 |
| 什么是事件溯源？ | 不存最终状态，存不可变事件流；状态=重放事件得出 |
| 事件溯源核心规则？ | append-only、事件不可变、状态可重放、事件是唯一事实源 |
| CQRS 和 ES 为什么常搭配？ | ES 给写侧提供事件源，投影构建读模型，天然解耦+最终一致 |
| 命令和事件的区别？ | 命令=请求意图（动词）；事件=已发生事实（过去式） |
| 聚合根怎么恢复状态？ | replay：按顺序 apply 事件；快照+增量重放加速 |
| 事件版本兼容怎么做？ | 新增字段给默认值；大改建新事件类型；版本号+升级器 |
| 最终一致性怎么缓解？ | 关键路径同步更新读模型 + 幂等消费 + 对账补偿 |
| 事件溯源缺点？ | 学习曲线陡、事件无限增长需快照归档、查询需投影、事件模型演进难 |
| 事务发件箱是什么？ | 业务变更与事件同一事务写 outbox 表，后台发 MQ，解决双写一致性 |
| 什么时候不该用 ES？ | 简单 CRUD、读模型无增值、无审计需求、团队无 DDD 经验 |

### 本章小结

- **CQRS 思想**（43.1）：读与写模型分离，三形态演进（单库分层 → 读写分库 → 独立读模型）；
- **事件溯源本质**（43.2）：事件即事实、append-only、状态=重放；与传统 CRUD/CDC 对照；
- **组合架构**（43.3）：命令侧聚合 + 事件存储 + 投影 + 读模型，最终一致性全景；
- **实战**（43.4）：账户转账手写 CQRS+ES——事件定义、聚合重放、事件存储（乐观锁）、投影器、快照；
- **工程细节**（43.5）：事件版本兼容、最终一致性代价、命令/事件命名规范；
- **选型**（43.6）：适用/反模式决策树、Axon/Eventuate/Outbox 等生态；
- **坑与实践**（43.7）：事件被改、重放非确定、投影不幂等、事件膨胀等 9 坑。

配套：第四十一章（聚合/仓储是建模基础）、第四十二章（状态机可做写侧流转引擎）、第三十三章 MySQL（事务存储）、第十六章 RabbitMQ / 第二十七章 Kafka（事件总线）、第二十四章 JVM（快照与性能）。

---

至此，第 43 章 CQRS 与事件溯源学习完成。下一章 [44-事件风暴工作坊实操.md](./44-事件风暴工作坊实操.md)（从业务便签到领域模型的建模入口）｜ 返回：[README.md](./README.md)
