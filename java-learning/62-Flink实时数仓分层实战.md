# 第六十二章 Flink 实时数仓分层实战（从 Lambda 到实时分层）

> 本章目标：第六十一章学会了 Flink 引擎，本章把它放进"数仓"工程——掌握实时数仓与离线数仓的本质差异、Lambda/Kappa 架构演进、ODS/DWD/DWS/ADS 四层实时分层设计与每层组件选型，用 Flink SQL + Kafka + MySQL/ES/ClickHouse 完成订单实时数仓全链路实战，并掌握维表关联、双流 Join、状态清理、数据质量等生产难题。
>
> 前置知识：第六十一章 Flink（引擎基础）、第二十七章 Kafka、第二十章 Elasticsearch、第三十三章 MySQL、第六十四章 ClickHouse/Doris 可衔接阅读。

## 62.1 实时数仓 vs 离线数仓：为什么要"实时"

| 维度 | 离线数仓（T+1） | 实时数仓（秒~分钟级） |
| --- | --- | --- |
| 数据时效 | 第二天才能查 | 秒级/分钟级可见 |
| 技术栈 | Hive/Spark SQL + 调度（定时跑） | Kafka + Flink + OLAP 引擎 |
| 典型场景 | 月报/经营分析/算法样本 | 大屏/风控/实时推荐/实时对账 |
| 计算模型 | 全量重算 | 增量流式 |

**为什么不能只用离线**：大促实时 GMV、实时风控拦截、实时库存预警——这些业务"等不到明天"。但**离线也不能被替代**：历史数据修正、复杂分析、成本考量。于是演进出两种架构。

## 62.2 架构演进：Lambda → Kappa → 实时分层

### 62.2.1 Lambda 架构（两条链路，维护两套代码）

```
            ┌── 批链路：全量数据 → Spark/Hive → 批结果表 ──┐
数据源 ──► 数据总线（Kafka）                                ├─► 服务层（合并两路结果）
            └── 流链路：实时数据 → Flink → 流结果表 ────┘
```

| 优点 | 缺点 |
| --- | --- |
| 批流各取所长（准确 + 实时） | **同一逻辑两套代码**，维护成本高 |
| 兼容历史修正 | 结果合并复杂、口径易漂移 |

### 62.2.2 Kappa 架构（只用流链路，重算替代补数）

```
数据源 ──► Kafka（保留全部历史）──► Flink（同一套代码）
                                    ├─ 重算：从最早 offset 重放 → 得到修正后结果
                                    └─ 即实时又准确（Kafka 够久就能重算）
```

| 优点 | 缺点 |
| --- | --- |
| 一套代码，逻辑统一 | Kafka 保留全部历史成本高 |
| 重放即可修正 | 复杂历史分析（多年）仍不如批 |

### 62.2.3 生产实践：批流一体 + 实时分层

```
主流选择：以实时分层（本章）为骨架，核心指标流批共用同一份 SQL 口径
  实时：Flink SQL 增量算 → 分钟级
  离线：Spark/Hive 每日全量对账 → 修正实时（实时为主、离线兜底对账）
```

> **一句话记忆**：Lambda 是"批流各写一遍"，Kappa 是"只用流、靠重放"，生产主流是"实时分层 + 离线对账"——既快又准还不重复造轮子。

## 62.3 实时数仓分层设计：ODS / DWD / DWS / ADS

```
业务库/埋点/日志
      │ binlog / 埋点上报
┌─────▼─────────────────────────────────────────────┐
│ ODS 贴源层：原样入 Kafka，不改结构、只加时间戳        │
│   topic: ods_order_info / ods_user_action           │
└─────┬─────────────────────────────────────────────┘
      │ Flink SQL 清洗（去重/补全/脱敏）
┌─────▼─────────────────────────────────────────────┐
│ DWD 明细层：明细事实 + 维表拉宽，业务语义就绪        │
│   topic: dwd_order_detail（订单×用户×商品维度）       │
└─────┬─────────────────────────────────────────────┘
      │ 窗口聚合（轻汇总）
┌─────▼─────────────────────────────────────────────┐
│ DWS 汇总层：按维度预聚合（分钟/小时粒度）            │
│   topic/表: dws_order_minute（分区/维度×时间桶）      │
└─────┬─────────────────────────────────────────────┘
      │ 应用层取数（大屏/报表/风控直接查）
┌─────▼─────────────────────────────────────────────┐
│ ADS 应用层：MySQL/ES/ClickHouse（多维分析）          │
│   大屏 GMV、实时榜单、漏斗分析、风险预警              │
└────────────────────────────────────────────────────┘
```

| 分层 | 做什么 | 存储 | 消费方式 |
| --- | --- | --- | --- |
| **ODS** | 原样接入、仅加工时间戳 | Kafka（保留 N 天） | 追加写 |
| **DWD** | 清洗、去重、维表拉宽 | Kafka / HDFS(落盘) | 明细可回溯 |
| **DWS** | 预聚合（秒/分/时粒度） | Kafka / OLAP 表 | 聚合后数据量骤降 |
| **ADS** | 应用取数、多维分析 | MySQL/ES/ClickHouse/Doris | 大屏/报表直接查 |

> **分层价值**：每层"瘦身"一次——ODS 原始全量 → DWD 明细标准化 → DWS 预聚合（数据量降 90%+）→ ADS 直接出数。**查询快是因为提前算好了**，实时数仓的本质是"用计算换查询延迟"。

## 62.4 每层组件选型：用什么存、用什么算

| 层 | 选型 | 理由 |
| --- | --- | --- |
| ODS | Kafka（原始日志流） | 高吞吐、可重放、天然衔接 Flink |
| DWD | Kafka +（落 HDFS/冰湖） | 明细可重放；落盘做离线对账 |
| DWS | Flink 窗口 + 状态 → Kafka/OLAP | 预聚合靠 Flink 状态与窗口 |
| ADS | MySQL（BI 报表）/ ES（检索）/ **ClickHouse-Doris（多维分析）** | 按查询场景选，详见 64 章 |
| 元数据/调度 | DataHub/Atlas、DolphinScheduler | 65 章平台治理详述 |

## 62.5 实战：订单实时数仓全链路（Flink SQL）

### 62.5.1 ODS 层：业务表 binlog + 埋点入 Kafka

```sql
-- ODS：业务库订单表（Flink CDC 接入，61.9）原样贴源
CREATE TABLE ods_order (
    order_id BIGINT, user_id BIGINT, sku_id BIGINT,
    amount DECIMAL(10,2), status INT, create_time TIMESTAMP(3),
    PRIMARY KEY (order_id) NOT ENFORCED
) WITH (
    'connector' = 'mysql-cdc',
    'hostname' = 'mysql-master', 'port' = '3306',
    'username' = 'root', 'password' = 'root',
    'database-name' = 'mall', 'table-name' = 't_order',
    'scan.startup.mode' = 'initial'
);

-- ODS：用户行为埋点（自定义 JSON）入 Kafka
CREATE TABLE ods_user_action (
    user_id BIGINT, action STRING, page STRING,
    ts TIMESTAMP(3), WATERMARK FOR ts AS ts - INTERVAL '5' SECOND
) WITH (
    'connector' = 'kafka',
    'topic' = 'ods-user-action',
    'properties.bootstrap.servers' = 'kafka:9092',
    'format' = 'json',
    'scan.startup.mode' = 'earliest-offset'
);
```

### 62.5.2 DWD 层：清洗 + 维表拉宽 + 双流 Join

```sql
-- 维表：商品信息（时态表——定期查询 MySQL 维表关联）
CREATE TABLE dim_sku (
    sku_id BIGINT, sku_name STRING, category_id BIGINT,
    price DECIMAL(10,2), PRIMARY KEY (sku_id) NOT ENFORCED
) WITH (
    'connector' = 'jdbc',
    'url' = 'jdbc:mysql://mysql-master:3306/mall',
    'table-name' = 'dim_sku',
    'username' = 'root', 'password' = 'root'
);

-- DWD：订单 × 商品维表拉宽（维度补齐）
CREATE TABLE dwd_order_detail AS
SELECT o.order_id, o.user_id, o.sku_id,
       s.sku_name, s.category_id,          -- 维表字段
       o.amount, o.create_time
FROM ods_order o
LEFT JOIN dim_sku FOR SYSTEM_TIME AS OF o.create_time s
     ON o.sku_id = s.sku_id
WHERE o.status = 1;                          -- 只算有效订单（清洗）

-- 双流 Join：订单流 × 支付流（同一订单的两条流对账）
CREATE TABLE dwd_order_pay AS
SELECT o.order_id, o.user_id, o.amount,
       p.pay_time
FROM dwd_order_detail o
LEFT JOIN ods_pay FOR SYSTEM_TIME AS OF o.create_time p
     ON o.order_id = p.order_id;
```

### 62.5.3 DWS 层：窗口预聚合（轻汇总）

```sql
-- DWS：每分钟 GMV（按 分类×分钟 预聚合）
CREATE TABLE dws_gmv_minute (
    category_id BIGINT, win_start TIMESTAMP(3), win_end TIMESTAMP(3),
    gmv DECIMAL(14,2), order_cnt BIGINT
) WITH ('connector' = 'kafka', 'topic' = 'dws-gmv-minute',
        'properties.bootstrap.servers' = 'kafka:9092', 'format' = 'json');

INSERT INTO dws_gmv_minute
SELECT category_id,
       TUMBLE_START(create_time, INTERVAL '1' MINUTE),
       TUMBLE_END(create_time, INTERVAL '1' MINUTE),
       SUM(amount), COUNT(DISTINCT order_id)
FROM dwd_order_detail
GROUP BY category_id, TUMBLE(create_time, INTERVAL '1' MINUTE);
```

### 62.5.4 ADS 层：落库供大屏/报表

```sql
-- ADS：分钟级 GMV 落 MySQL（大屏轮询）或 ClickHouse（多维分析，64 章）
CREATE TABLE ads_gmv (
    category_id BIGINT, stat_time TIMESTAMP(3), gmv DECIMAL(14,2)
) WITH ('connector' = 'jdbc',
        'url' = 'jdbc:mysql://mysql-master:3306/ads',
        'table-name' = 'ads_gmv', 'username' = 'root', 'password' = 'root');

INSERT INTO ads_gmv
SELECT category_id, win_end, gmv FROM dws_gmv_minute;
```

> **实战要点**：这套全链路**全部用 Flink SQL**（61.7 的扩展）——建表即连数据源，INSERT INTO 即流式作业，一个文件跑完 ODS→DWD→DWS→ADS。

## 62.6 实时数仓的三个生产难题

### 62.6.1 维表关联（时态表）的性能

| 方案 | 原理 | 适用 |
| --- | --- | --- |
| 同步查库（JDBC） | 每条数据查 MySQL | 简单但慢（禁用于高吞吐） |
| **维表缓存（LRU）** | 内存缓存 + 定期刷新 | 高吞吐首选 |
| 维表异步 IO | Async I/O 并发查 | 高吞吐 + 查库 |
| 广播维表 | 小维表广播到所有并行 | 小表（<100MB）最佳 |

> **实战口诀**：小维表（用户/商品字典）用**广播**，中等维表用 **LRU 缓存 + 异步 IO**，大维表（亿级）进状态或换 65 章维度建模方案。

### 62.6.2 状态无限增长

DWD 双流 Join、去重都依赖状态（保留等待匹配的数据），不加 TTL 会内存爆掉：

```sql
-- 建表时声明状态 TTL（Flink SQL）
CREATE TABLE dwd_order_pay (...) WITH (
    'table.exec.state.ttl' = '1 h'      -- 状态只保留 1 小时
);
```

> **规则**：Join 的状态 TTL ≥ 业务允许的最大延迟（如支付最长 30 分钟 → TTL 1 小时）；去重状态 TTL 覆盖业务去重窗口。

### 62.6.3 数据质量与对账

| 问题 | 对策 |
| --- | --- |
| 迟到数据 | Watermark + 旁路流（61.4~61.5）补数 |
| 数据倾斜 | 热点 key 加盐拆散，聚合后合并（63 章 Spark 同技巧） |
| 口径不一致 | 实时/离线共用同一份 SQL 模板，每日对账 |
| binlog 乱序 | CDC 配 primary key，Flink 按 key 乱序处理 |
| 反压积压 | 监控背压，临时扩容并行度 |

## 62.7 实时数仓的坑与优化清单

**踩坑清单**：

| 坑 | 后果 | 对策 |
| --- | --- | --- |
| DWD 层做重聚合 | 明细层被污染 | DWD 只做明细标准化，聚合放 DWS |
| 维表直接查库 | 高吞吐被打挂 | 广播/LRU/异步 IO |
| 状态无 TTL | OOM 频繁重启 | 按业务延迟设 TTL |
| ODS 直接改结构 | 原始数据不可追溯 | ODS 原样保留，加工全部下推 |
| 无离线对账 | 口径漂移无人知 | 每日离线对账修正实时 |
| Kafka 分区 < 并行度 | 部分并行空转 | 分区数 ≥ 作业最大并行度 |

**优化清单**：MiniBatch 微批（减少 shuffle）、本地聚合（两阶段聚合）、维表缓存调参（容量/刷新间隔）、Source 并行度对齐 Kafka 分区。

## 62.8 面试速查与本章小结

| 问题 | 一句话答案 |
| --- | --- |
| Lambda 和 Kappa 区别？ | 批流两套代码 vs 单流重放；生产用实时分层+离线对账 |
| 实时数仓为什么分层？ | 每层瘦身一次，用计算换查询延迟 |
| ODS/DWD/DWS/ADS 各自职责？ | 贴源/明细标准/预聚合/应用取数 |
| 维表关联怎么做才快？ | 小表广播、中表 LRU+异步 IO、大表进状态 |
| 实时数仓状态爆了怎么办？ | 状态 TTL + 本地聚合 + 并行度对齐 |
| 实时和离线口径不一致？ | 共用 SQL 模板 + 每日对账修正 |

- **架构**（62.1~62.2）：实时数仓解决"等不到明天"，Lambda/Kappa/分层演进；
- **分层**（62.3~62.4）：ODS→DWD→DWS→ADS 每层瘦身，组件各司其职；
- **实战**（62.5）：Flink SQL 一条链路跑完四层（CDC 入 ODS、维表拉宽 DWD、窗口聚合 DWS、落库 ADS）；
- **生产**（62.6~62.7）：维表缓存、状态 TTL、离线对账是三大必修课，踩坑清单即生产守则。

下一章：[63-Spark体系.md](./63-Spark体系.md)（批处理与批流一体的另一半）｜ 返回：[README.md](./README.md)
