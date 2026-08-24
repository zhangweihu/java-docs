# 第六十一章 Flink 流式计算（从批处理思维到实时流处理）

> 本章目标：第二十七章学了 Kafka（消息管道），本章学**流式计算引擎**——掌握 Flink 的定位与核心架构（JobManager/TaskManager/并行度）、编程模型（DataStream API 算子链）、时间语义与 Watermark（乱序处理的核心）、窗口计算（滚动/滑动/会话）、状态与容错（Checkpoint/exactly-once）、Flink SQL 与 Kafka 集成，完成一个实时订单统计实战，理解 Flink CDC 与部署方式，并能在 Spark Streaming / Kafka Streams / Flink 之间做出选型判断。
>
> 前置知识：第二十七章 Kafka（分区/消费者组/offset）、第二十章 Elasticsearch（实时检索）、第二十六章 Netty（网络与并发模型）、Java 8 Lambda 与 Stream（第四、三章）。

## 61.1 为什么需要流式计算：批 vs 流的本质差异

| 维度 | 批处理（MapReduce/Spark Batch） | 流处理（Flink/Spark Streaming） |
| --- | --- | --- |
| 数据形态 | 有界数据集（存在文件/表中） | 无界数据流（持续产生） |
| 处理时机 | 攒一批再算（T+1/小时级） | 来一条算一条（毫秒级延迟） |
| 结果时效 | 事后分析 | 实时响应（大屏/风控/推荐） |
| 处理模型 | 全量扫描 | 增量+状态 |

```
为什么不能只用批处理？
风控要"秒级拦截欺诈"、大屏要"实时刷新 GMV"、推荐要"实时更新画像"——
这些场景数据永远在来，等你"攒够一批"再算，业务已经等不及了。
```

**Flink 的定位**：统一批流——同一套 API 既能处理有界（批）也能处理无界（流），并承诺**毫秒级延迟 + exactly-once 语义**。这是它区别于早期流引擎的杀手锏。

> **一句话记忆**：批处理是"算已发生的事"，流处理是"边发生边算"。Flink 的核心能力是"边发生边算，而且算得准（exactly-once）、挂了还能续算（Checkpoint）"。

## 61.2 Flink 核心架构：JobManager + TaskManager

```
                     ┌─────────────────────────────┐
                     │   JobManager（作业管理器）      │
                     │   调度任务 / Checkpoint 协调    │
                     │   故障恢复 / 作业元数据          │
                     └──────────────┬──────────────┘
                                    │ 分配 Slot / 心跳
              ┌─────────────────────┼─────────────────────┐
              │                     │                     │
     ┌────────▼────────┐   ┌────────▼────────┐   ┌────────▼────────┐
     │ TaskManager（TM） │   │ TaskManager（TM） │   │ TaskManager（TM） │
     │  Slot 0 │ Slot 1  │   │  ...             │   │  ...             │
     │ 并行执行 Task 的子任务 │   │                  │   │                  │
     └─────────────────┘   └─────────────────┘   └─────────────────┘
```

| 组件 | 职责 | 类比 |
| --- | --- | --- |
| **JobManager** | 作业调度、Checkpoint 协调、故障恢复 | 项目经理 |
| **TaskManager** | 真正执行算子的容器，一个 TaskManager 多个 Slot | 工人（Slot=工位） |
| **并行度（Parallelism）** | 算子被拆成几个并行子任务 | 几个工位同时干 |
| **Slot** | 资源单元（内存/CPU），决定最大并行度 | 工位数 |

```
并行度真相：
source（Kafka 3 分区）→ map（并行 3）→ keyBy → window（并行 3）→ sink（并行 3）
并行度 = 数据被切成几路并行计算；瓶颈算子的并行度决定整体吞吐
```

> **面试题**：JobManager 挂了怎么办？答：Flink 1.9+ 支持 JobManager 高可用——依赖 ZooKeeper/K8s 选主 + 持久化作业元数据（Checkpoint 都在外部存储），新的 JobManager 接管后从最近 Checkpoint 恢复，作业自动拉起。

## 61.3 编程模型：DataStream API 与算子

Flink 编程分三层：SQL/Table API（最上层，声明式）→ DataStream API（核心，命令式）→ ProcessFunction（最底层，精确控制）。本章以 DataStream 为主。

### 61.3.1 骨架：Source → Transform → Sink

```java
// 实时订单金额统计（每分钟输出一次），完整可运行骨架
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

DataStream<String> lines = env.addSource(                     // ① Source
        new FlinkKafkaConsumer<>("order-topic",
                new SimpleStringSchema(),
                kafkaProps));

DataStream<Order> orders = lines.map(line -> {                // ② Transform：解析
    // JSON → Order 对象
    return new Order(json.getLong("orderId"),
                     json.getString("userId"),
                     json.getLong("amount"),
                     json.getLong("ts"));
});

orders.keyBy(Order::getUserId)                                // ③ 按键分组（同用户同状态）
      .timeWindow(Time.minutes(1))                            // ④ 窗口：每分钟
      .sum("amount")                                          // ⑤ 聚合
      .map(Order -> String.format("用户 %s 近 1 分钟消费 %d 元", ...))
      .addSink(new FlinkKafkaProducer<>("result-topic",       // ⑥ Sink
              new SimpleStringSchema(), kafkaProps));

env.execute("realtime-order-stat");                           // ⑦ 提交作业
```

### 61.3.2 常用算子速查

| 算子 | 作用 | 示例 |
| --- | --- | --- |
| `map` | 一进一出（转换） | `map(x -> x * 2)` |
| `flatMap` | 一进多出/过滤 | `flatMap(line -> Arrays.stream(line.split(" ")))` |
| `filter` | 过滤 | `filter(x -> x > 100)` |
| `keyBy` | 按键分组（逻辑分区，不是 shuffle 排序） | `keyBy(order -> order.getUserId())` |
| `reduce`/`sum` | 分组内聚合 | `keyBy(...).sum("amount")` |
| `window` | 开窗（见 61.5） | `.timeWindow(Time.minutes(5))` |
| `connect`/`coMap` | 合并两条流 | 订单流 + 商品流关联 |
| `ProcessFunction` | 最底层：访问时间/状态/定时器 | 复杂事件处理 |

> **记住**：`keyBy` 之后算子的状态和窗口是**按 key 隔离**的——同一个 key 的数据一定被同一个子任务处理，这是"同用户统计正确"的保证。

## 61.4 时间语义与 Watermark：处理乱序的核心

流处理的三大时间（面试必问）：

| 时间 | 含义 | 举例 | 用途 |
| --- | --- | --- | --- |
| **Event Time（事件时间）** | 数据发生的时间 | 订单支付时间戳 | 业务统计的正确基准 |
| **Ingestion Time（摄入时间）** | 进入 Flink 的时间 | 接入时刻 | 介于两者之间 |
| **Processing Time（处理时间）** | 算子处理的时间 | 机器当前时间 | 追求低延迟、不关心乱序 |

**为什么需要 Watermark（水位线）**：

```
网络延迟/重试导致数据乱序：
事件时间：10:00:01、10:00:02、10:00:03、10:00:02(迟到)、10:00:04
如果不处理乱序，窗口 10:00:00~10:00:59 在 10:01:00 关闭时，
10:00:02 那条迟到的数据就被漏掉了 → 统计不准

Watermark 解法：
Watermark = 已观测到的最大事件时间 - 允许乱序延迟（如 5s）
窗口只有等到 Watermark 越过窗口边界才触发计算
→ 等 5 秒，让迟到的数据进窗口，统计才准
```

```java
// 设置 Event Time + Watermark（乱序延迟 5 秒）
DataStream<Order> withWatermark = orders
        .assignTimestampsAndWatermarks(
                WatermarkStrategy.<Order>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner((order, ts) -> order.getTs()));
```

| 配置 | 值 | 效果 |
| --- | --- | --- |
| `forBoundedOutOfOrderness(5s)` | 允许乱序 5 秒 | 等 5 秒再关窗，丢数据少 |
| `forMonotonousTimestamps()` | 不允许乱序 | 乱序数据直接丢/延迟 |
| 延迟容忍越大 | 越准 | 结果越晚出（延迟与准确性权衡） |

> **一句话记忆**：Watermark 是 Flink 说"我最多再等这么久"的信号——它让窗口知道"什么时候该关门算账"，从而在"算得准"与"出得快"之间找平衡。

## 61.5 窗口计算：滚动 / 滑动 / 会话

| 窗口类型 | 规则 | 适用 |
| --- | --- | --- |
| **滚动窗口**（Tumbling） | 固定大小、不重叠 | 每分钟 GMV、每 5 分钟 UV |
| **滑动窗口**（Sliding） | 固定大小 + 固定滑动步长、可重叠 | 每 10 秒看最近 1 分钟趋势 |
| **会话窗口**（Session） | 空闲 N 时长结束 | 用户行为会话、在线时长 |
| 计数窗口 | 攒够 N 条触发 | 凑批处理 |

```java
// 滚动窗口：每 5 分钟统计一次所有订单金额
orders.timeWindow(Time.minutes(5)).sum("amount");

// 滑动窗口：每 10 秒统计最近 1 分钟订单数
orders.timeWindow(Time.minutes(1), Time.seconds(10)).sum("amount");

// 会话窗口：用户 30 分钟无新行为则结束一个会话
orders.keyBy(Order::getUserId)
      .window(EventTimeSessionWindows.withGap(Time.minutes(30)));
```

**迟到数据的三层处理**（窗口触发后，迟到的数据怎么办）：

| 手段 | 做法 | 适用 |
| --- | --- | --- |
| 允许迟到（allowedLateness） | 窗口触发后仍等 N 秒，迟到的数据触发增量计算 | 大多数场景 |
| 迟到数据重算（sideOutputLateData） | 超过容忍度的迟到数据进旁路流，单独处理/补数 | 对账、告警 |
| 直接丢弃 | 默认行为 | 可容忍少量丢失 |

## 61.6 状态与容错：Checkpoint 与 exactly-once

流计算"挂了要能续算"靠**状态**，状态是算子的"记忆"：

| 概念 | 说明 |
| --- | --- |
| **算子状态** | 算子级别共享（如 Kafka 分区 offset） |
| **键控状态（Keyed State）** | 按 key 隔离（如每个用户的累计消费金额） |
| **Checkpoint** | 周期性把状态快照存到外部（HDFS/S3），故障自动恢复 |
| **Savepoint** | 手动触发的一致性快照，用于升级/回滚/迁移 |

**Checkpoint 的 two-phase commit（两阶段提交）保证 exactly-once**：

```
Flink + Kafka Sink 的端到端 exactly-once：
① 算子先做预提交（Pre-commit）：状态写入外部事务
② Checkpoint 完成 → 通知所有 Sink 提交（Commit）
③ 故障 → 回滚到最近 Checkpoint，从 Kafka 指定 offset 重放
   → 配合 Kafka 幂等/事务机制，数据"不丢不重"
```

```java
// 开启 exactly-once 检查点
env.enableCheckpointing(60000);                                  // 每 60s 一次
env.getCheckpointConfig().setCheckpointingMode(
        CheckpointingMode.EXACTLY_ONCE);
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30000);  // 两次间最小间隔
```

> **注意**：exactly-once 是有代价的（更多网络交互、吞吐下降），且需要**上下游都支持**（Kafka 开启幂等 + 事务、MySQL sink 支持幂等）。不是所有场景都值得，对账/统计类用 at-least-once + 幂等也常见。

## 61.7 Flink SQL：声明式流处理

DataStream 写业务逻辑繁琐，Flink SQL 让"流"像"表"一样写 SQL（声明式、自动优化），是生产主力：

```sql
-- 实时订单统计：按商品分类每 5 分钟滚动聚合
CREATE TABLE orders (
    order_id    BIGINT,
    user_id     BIGINT,
    amount      DECIMAL(10,2),
    category    STRING,
    ts          TIMESTAMP(3),
    WATERMARK FOR ts AS ts - INTERVAL '5' SECOND      -- 自动生成 Watermark
) WITH (
    'connector' = 'kafka',
    'topic' = 'order-topic',
    'properties.bootstrap.servers' = 'localhost:9092',
    'format' = 'json',
    'scan.startup.mode' = 'earliest-offset'
);

CREATE TABLE result_sink (
    category    STRING,
    total_amount DECIMAL(10,2),
    window_time  TIMESTAMP(3)
) WITH (
    'connector' = 'jdbc',
    'url' = 'jdbc:mysql://localhost:3306/stat',
    'table-name' = 'category_amount',
    'username' = 'root', 'password' = 'root'
);

INSERT INTO result_sink
SELECT category, SUM(amount) AS total_amount, TUMBLE_END(ts, INTERVAL '5' MINUTE)
FROM orders
GROUP BY category, TUMBLE(ts, INTERVAL '5' MINUTE);
```

| 能力 | 说明 |
| --- | --- |
| 流式 JOIN | 双流 Join（按窗口/时间条件，可处理乱序） |
| 窗口函数 | TUMBLE/HOP/SESSION 窗口 |
| 时态表 | 维表关联（如订单 join 商品维表，走 HBase/MySQL 查询） |
| Upsert/CDC | 配合 changelog 支持实时数仓 |

> **架构趋势**：Flink SQL 已成为实时数仓（实时分层：ODS→DWD→DWS→ADS）的核心引擎，与 27 章 Kafka、20 章 ES、MySQL 搭配形成"实时链路"。

## 61.8 实战：实时订单统计大屏（Kafka + Flink + MySQL/ES）

```
订单服务（写 MySQL）──► 发 Kafka（order-topic）
                           │
                     Flink 实时作业（61.7 SQL）
                     ├─ 每分钟 GMV、订单数 → MySQL 结果表 → 大屏定时刷新
                     ├─ 实时 UV / 热销商品 → Redis/ES → 大屏实时渲染
                     └─ 异常大额订单 → 告警 Topic → 风控
```

| 环节 | 技术 | 对应章节 |
| --- | --- | --- |
| 消息管道 | Kafka（分区保证消费并发） | 27 章 |
| 流计算 | Flink（窗口/状态/Watermark） | 本章 |
| 结果存储 | MySQL（汇总）/ Redis（实时计数）/ ES（明细检索） | 33/34/20 章 |
| 可视化 | 大屏定时轮询 / WebSocket 推送 | 09 章 |

**生产要点**：Kafka 分区数 = Flink 并行度的参考基准（1 分区 → 最多 1 并行）；消费 offset 提交交给 Checkpoint 管理，避免"手动提交丢数据"。

## 61.9 Flink CDC：数据库变更实时捕获

Flink CDC 基于 binlog（与 59 章 Canal 同源，但集成更紧密）：

```
MySQL binlog ──► Flink CDC Source（模拟从库读 binlog）
                  ├─ 全量 + 增量一体化（先快照后实时）
                  └─ 输出 Changelog（INSERT/UPDATE/DELETE）→ 实时数仓/ES/缓存
```

```java
// Flink SQL 形式：把 MySQL 表变成实时数据流
CREATE TABLE user_cdc (
    id INT, name STRING, updated_at TIMESTAMP(3),
    PRIMARY KEY (id) NOT ENFORCED
) WITH (
    'connector' = 'mysql-cdc',
    'hostname' = 'localhost', 'port' = '3306',
    'username' = 'root', 'password' = 'root',
    'database-name' = 'mall', 'table-name' = 't_user'
);

-- 实时同步到 ES：用户表任何变更秒级同步
INSERT INTO user_es SELECT * FROM user_cdc;
```

| 应用 | 说明 |
| --- | --- |
| 实时数仓入仓 | 业务库变更实时进数仓（替代 T+1） |
| 缓存/ES 同步 | 数据库变更实时同步到 Redis/ES（59 章迁移的实时版） |
| 业务解耦 | binlog 事件驱动下游业务 |

## 61.10 部署方式：独立 / YARN / K8s

| 方式 | 说明 | 适用 |
| --- | --- | --- |
| 独立集群（Standalone） | 自己起 JobManager/TM 进程 | 学习、小规模 |
| YARN（on YARN） | 提交到 Hadoop YARN，资源动态申请 | 已有大数据集群 |
| **K8s（Native）** | 容器化部署，弹性伸缩、与 49 章云原生衔接 | 生产主流 |

```
Flink on K8s（生产推荐）：
作业提交 = 创建 FlinkDeployment CRD（Flink Operator，56 章 Operator 模式）
→ 自动起 JobManager/TaskManager Pod、滚动升级、保存点管理
→ 弹性：TaskManager 可自动扩缩容
```

> **衔接**：部署 Flink 的过程与 49 章（K8s 部署）、56 章（Operator 模式）天然契合——Flink 官方 Operator 就是"把 Flink 集群运维写成代码"的典型。

## 61.11 选型对比：Flink vs Spark Streaming vs Kafka Streams

| 维度 | **Flink** | **Spark Streaming/Structured** | **Kafka Streams** |
| --- | --- | --- | --- |
| 处理模型 | 真正流式（逐条） | 微批（攒小批）/结构化流 | 流式（Kafka 生态内） |
| 延迟 | 毫秒级 | 秒级（微批） | 毫秒级 |
| 状态管理 | 强大（键控状态） | 有（Structured 支持） | 有限 |
| exactly-once | 端到端强 | 强（输出端需支持） | 有（配合 Kafka 事务） |
| 复杂度 | 高（需集群运维） | 中 | 低（嵌入应用） |
| 适用 | 复杂实时计算、数仓 | 批流一体、已有 Spark 生态 | 简单流处理、Kafka 生态内 |

```
选型决策：
简单消息处理（过滤/转换/路由）→ Kafka Streams（轻量，嵌入应用）
需要窗口/状态/复杂计算、毫秒级 → Flink（首选）
已有 Spark 生态、批流都要 → Spark Structured Streaming
```

> **一句话记忆**：Kafka Streams 是"管道里的过滤器"，Spark 是"攒批的工厂"，Flink 是"边到边流水线还带精确记账"。要复杂实时计算选 Flink，要简单就在 Kafka 生态内解决。

## 61.12 生产踩坑清单与面试速查

**踩坑清单**：

| 坑 | 后果 | 对策 |
| --- | --- | --- |
| 没配 Watermark/用错时间语义 | 统计结果偏差 | 业务统计一律 Event Time + Watermark |
| 并行度超过 Kafka 分区数 | 部分并行空闲，资源浪费 | 并行度 ≤ 分区数 |
| 手动提交 offset + Checkpoint 冲突 | 重复/丢失 | 交给 Checkpoint 管理 |
| 状态无限增长 | 内存 OOM/恢复慢 | 状态 TTL（`setTtl`）+ 定期清理 |
| 窗口不触发 | 时间戳单位错（秒/毫秒） | 统一时间单位（毫秒） |
| 反压不处理 | 延迟飚升、数据积压 | 监控背压指标，优化算子/扩容 |
| 升级无 Savepoint | 状态丢失 | 版本升级必打 Savepoint |

**面试速查**：

| 问题 | 一句话答案 |
| --- | --- |
| Flink 和 Spark Streaming 区别？ | 真流 vs 微批，延迟毫秒 vs 秒级 |
| Watermark 是什么？ | 乱序容忍信号，决定窗口何时触发 |
| 三种时间语义？ | Event（发生）/Ingestion（进入）/Processing（处理） |
| exactly-once 怎么实现？ | Checkpoint + 两阶段提交，需要上下游配合 |
| 背压是什么？怎么解决？ | 下游处理慢顶住上游；监控+优化算子+扩容 |
| Flink 状态存在哪？ | 本地（RocksDB/堆内存）+ 定期 Checkpoint 到外部存储 |
| 为什么用 Flink 做实时数仓？ | 低延迟 + SQL 声明式 + 状态容错 + 批流一体 |

### 本章小结

- **定位**（61.1）：流处理 = 边发生边算，Flink 统一批流 + exactly-once；
- **架构**（61.2）：JobManager 调度 + TaskManager 执行 + Slot/并行度，HA 依赖外部协调；
- **编程**（61.3）：Source → Transform → Sink 骨架，keyBy 后状态按 key 隔离；
- **时间**（61.4~61.5）：Event Time + Watermark 治乱序，三类窗口适配不同统计；
- **容错**（61.6）：Checkpoint + 两阶段提交 = exactly-once，Savepoint 管升级；
- **SQL**（61.7~61.9）：Flink SQL 是实时数仓主力，Flink CDC 打通业务库实时入仓；
- **部署选型**（61.10~61.12）：K8s 原生部署为主，Flink vs Spark vs Kafka Streams 按需选择，踩坑清单即生产守则。

下一章 [62-Flink实时数仓分层实战.md](./62-Flink实时数仓分层实战.md)（把引擎装进数仓工程）｜ 返回：[README.md](./README.md)
