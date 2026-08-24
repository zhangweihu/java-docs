# 第六十四章 ClickHouse 与 Doris 分析引擎（秒级响应的 OLAP 分析引擎）

> 本章目标：第六十二、六十三章解决了"数据怎么算出来"，本章解决"算出来的数据怎么秒查"——掌握 OLTP 与 OLAP 的本质差异、列式存储的加速原理、ClickHouse 的 MergeTree 架构与实战（建表/分区/物化视图/与 MySQL 数据同步）、Apache Doris 的 FE/BE MPP 架构与聚合模型，最后用一张选型表回答 ClickHouse / Doris / ES / MySQL 的经典选择题。
>
> 前置知识：第三十三章 MySQL（行存）、第六十二章 实时数仓（DWS/ADS 落库）、第六十三章 Spark（批量加工）、第二十章 Elasticsearch。

## 64.1 OLTP vs OLAP：为什么分析查询要专用引擎

| 维度 | OLTP（MySQL 等） | OLAP（ClickHouse/Doris 等） |
| --- | --- | --- |
| 典型查询 | 按 ID 增删改查（点查） | 大范围聚合统计（按时间/维度） |
| 数据量 | 百万~千万行 | 亿~千亿行 |
| 读写模式 | 高频小写 | 批量写入 + 高频读 |
| 存储结构 | **行存** | **列存** |
| 核心诉求 | 事务一致、低延迟点查 | 秒级响应大聚合 |

```
为什么列存对分析快？
行存（MySQL）：按行存 → 查"所有订单的金额总和"要读整张表所有行
列存（ClickHouse）：按列存 → 只读 amount 这一列的数据块，其余列不读

分析查询 90% 是"只取少数列做聚合"，列存天然跳过无关列
另外列同类型 → 压缩率极高（10 倍+）→ 磁盘 IO 骤减
```

> **一句话记忆**：行存为"取一行"优化，列存为"算一列"优化。分析查询就是"拼命算某一列"，所以列存 + 高压缩 = 秒级响应。

## 64.2 ClickHouse 架构：MergeTree 家族

### 64.2.1 核心存储引擎 MergeTree

```
MergeTree 写入流程：
批量插入 → 生成不可变数据分片（part）→ 后台定期 Merge（合并小 part 成大 part）
查询时只扫描相关分区 + 分区内按索引（稀疏索引）跳过无关块

分区（PARTITION BY）：按时间分区 → 查询自动裁剪分区
排序键（ORDER BY）：列内有序 → 稀疏索引 + 跳数索引加速
主键（PRIMARY KEY）：与排序键同构，支持点查与范围裁剪
```

```sql
-- 典型建表：电商订单分析表（按天分区）
CREATE TABLE order_analysis (
    order_id    UInt64,
    user_id     UInt64,
    sku_id      UInt64,
    amount      Decimal(18,2),
    status      String,
    stat_date   Date
) ENGINE = MergeTree
PARTITION BY toYYYYMM(stat_date)          -- 按月分区
ORDER BY (stat_date, user_id)             -- 排序键（稀疏索引）
TTL stat_date + INTERVAL 1 YEAR;          -- 数据一年后自动清理
```

| MergeTree 家族 | 用途 |
| --- | --- |
| MergeTree | 基础（主表） |
| ReplacingMergeTree | 按主键去重（覆盖更新） |
| SummingMergeTree | 按主键累加（预聚合） |
| AggregatingMergeTree | 物化聚合（配合物化视图） |
| Distributed | 分布式表（集群跨节点） |

### 64.2.2 ClickHouse 集群与 Java 接入

```
ClickHouse 集群：
本地表（每个节点一份数据分片）+ 分布式表（Distributed，逻辑入口）
→ 查询自动分散到各分片并行计算（MPP 风格）+ 副本冗余

Java 接入（JDBC 兼容，就像连 MySQL）：
String url = "jdbc:clickhouse://ck-node:8123/mall_ads";
try (Connection conn = DriverManager.getConnection(url, "default", "")) {
    ResultSet rs = conn.createStatement()
        .executeQuery("SELECT category_id, SUM(amount) FROM ads_gmv GROUP BY category_id");
}
```

> **注意**：ClickHouse 强在聚合分析，**不擅长点查更新与事务**（无完整事务/行级更新弱）——它管"分析"，不碰"业务库"。

## 64.3 ClickHouse 实战：实时数仓 ADS 层落地

```sql
-- 62 章 ADS 层可落 ClickHouse：分钟级 GMV 多维分析
CREATE TABLE ads_gmv (
    category_id UInt64,
    stat_time   DateTime,
    gmv         Decimal(18,2)
) ENGINE = MergeTree
ORDER BY (category_id, stat_time);

-- 直接查：按分类统计当天 GMV（亿行数据也是秒级）
SELECT category_id, SUM(gmv) AS total_gmv
FROM ads_gmv
WHERE stat_time >= today()
GROUP BY category_id
ORDER BY total_gmv DESC
LIMIT 10;

-- 物化视图：实时聚合结果存另一张表（查询更快）
CREATE MATERIALIZED VIEW mv_gmv_hourly
ENGINE = SummingMergeTree
ORDER BY (category_id, toStartOfHour(stat_time))
AS SELECT category_id, toStartOfHour(stat_time) AS hour,
          SUM(gmv) AS gmv
   FROM ads_gmv GROUP BY category_id, toStartOfHour(stat_time);
```

**与 62 章实时数仓衔接**：Flink DWS 预聚合结果 → Kafka → 落 ClickHouse（或其他工具）→ 大屏/报表/Ad-hoc 分析秒查。**ClickHouse 是实时数仓 ADS 层的标配之一**。

## 64.4 Apache Doris 架构：FE/BE 与 MPP

| 组件 | 职责 |
| --- | --- |
| **FE**（Frontend） | 元数据管理、SQL 解析/规划、查询分发（类似 Driver） |
| **BE**（Backend） | 数据存储与计算执行（类似 Executor），节点间 MPP 并行 |
| **MPP** | 查询拆成子计划并行到多 BE，结果汇总（与 Spark 的"先算完再合并"不同，Doris 是"边算边换数据"流水线） |

```
Doris 三大模型（写入时的数据组织方式）：
① 明细模型（Duplicate）：原样保留，可任意聚合查询
② 聚合模型（Aggregate）：写入即按维度聚合（SUM/MAX/MIN），省存储加速查询
③ 唯一模型（Unique）：按主键去重覆盖（实时更新的业务宽表）
```

```sql
-- 聚合模型示例：订单表按 用户×日期 预聚合
CREATE TABLE dws_user_daily (
    user_id   BIGINT,
    stat_date DATE,
    order_cnt BIGINT   SUM,     -- 聚合模型：写入时 SUM 合并
    gmv       DECIMAL(18,2) SUM
)
DUPLICATE KEY (user_id, stat_date)   -- 或 UNIQUE KEY / AGGREGATE KEY
DISTRIBUTED BY HASH(user_id) BUCKETS 10;
```

| 对比 | Doris | ClickHouse |
| --- | --- | --- |
| 架构 | FE/BE 原生 MPP，无分片概念 | 本地表+分布式表两层 |
| 数据模型 | 三模型（明细/聚合/唯一） | MergeTree 家族 |
| 更新 | 唯一模型支持主键更新（强） | 更新弱（靠 ReplacingMergeTree） |
| 查询 | 标准 SQL（MySQL 协议兼容） | 扩展 SQL |
| 生态 | 支持 MySQL 协议、BI 工具友好 | 官方驱动/生态较独立 |

## 64.5 实战选型：ClickHouse / Doris / ES / MySQL

| 引擎 | 强项 | 弱项 | 场景 |
| --- | --- | --- | --- |
| **MySQL** | 事务、点查、业务库 | 大聚合慢、亿级吃力 | 业务主库（33 章） |
| **ES** | 全文检索、模糊查询、日志 | 聚合不如列存 | 日志检索、订单/商品搜索（20 章） |
| **ClickHouse** | 超大规模聚合（千亿级）、压缩率高 | 更新弱、并发写一般 | 离线/实时大宽表分析 |
| **Doris** | 统一多表关联分析、主键更新、MySQL 协议 | 生态较新 | 实时数仓统一分析层、报表 BI |

```
选型决策树：
要业务事务/点查 → MySQL（33 章）
要全文搜索/日志检索 → ES（20 章）
要亿级大聚合秒查（单表大） → ClickHouse
要数仓统一分析（多表 Join + 更新 + 报表） → Doris
```

> **生产常见组合**：业务库 MySQL（OLTP）+ 实时链路 Flink → Kafka（62 章）+ 分析层 Doris/ClickHouse（OLAP）+ 检索层 ES，各司其职、各用所长。

## 64.6 面试速查与本章小结

| 问题 | 一句话答案 |
| --- | --- |
| 列存为什么分析快？ | 只读相关列 + 同类型高压缩 + 稀疏索引跳块 |
| ClickHouse 和 Doris 区别？ | 单表聚合王者 vs 多表统一分析+主键更新 |
| MergeTree 是什么？ | 分区+排序键+后台合并，写入不可变块、查询裁剪 |
| OLTP 和 OLAP 怎么分？ | 点查事务 vs 大聚合分析，行存 vs 列存 |
| 实时数仓分析层用啥？ | 亿级聚合选 ClickHouse，统一数仓选 Doris |
| Doris 三大模型？ | 明细/聚合/唯一，分别应对不同写入需求 |

- **本质**（64.1）：OLAP 用列存换聚合速度，秒级响应来自"少读列+高压缩"；
- **ClickHouse**（64.2~64.3）：MergeTree 分区+排序键+物化视图，ADS 层秒查标配；
- **Doris**（64.4）：FE/BE MPP + 三数据模型，数仓统一分析层；
- **选型**（64.5~64.6）：MySQL 管业务、ES 管检索、CK 管大聚合、Doris 管统一分析，组合使用。

下一章：[65-大数据平台全景.md](./65-大数据平台全景.md)（从零搭建企业级数据平台，全系列收官）｜ 返回：[README.md](./README.md)
