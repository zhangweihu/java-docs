# 第六十三章 Spark 体系（批处理与批流一体的大数据引擎）

> 本章目标：第六十一、六十二章学了 Flink（流为主），本章补上 Spark——掌握 Spark 的定位与架构（Driver/Executor、DAG、宽窄依赖、Shuffle）、核心抽象 RDD/DataFrame/Dataset 的演进关系、Spark SQL 与 Hive 集成、Structured Streaming 流处理、数据倾斜与调优三板斧，最后用对比表回答"Spark 还是 Flink"的经典选择题。
>
> 前置知识：第六十一章 Flink 流式计算（引擎概念对比）、第二十七章 Kafka、Java 8 Lambda/Stream（第三章、四章）。

## 63.1 Spark 是什么：批处理的事实标准

| 维度 | Spark | 传统 Hadoop MapReduce |
| --- | --- | --- |
| 中间结果 | **内存缓存**（RDD 内存计算） | 落盘（每步写 HDFS） |
| 迭代计算 | 快（复用内存数据） | 慢（反复读写磁盘） |
| 易用性 | DataFrame/SQL 声明式 | 手写 Mapper/Reducer |
| 场景 | 批处理 / SQL / 机器学习 / 流 | 已被 Spark 取代（仅存量） |

```
Spark 生态全家桶：
Spark Core（基础 RDD/调度）→ Spark SQL（结构化查询）→ Spark MLlib（机器学习）
→ Spark Streaming / Structured Streaming（流）→ GraphX（图计算）
```

> **一句话记忆**：Spark 把 MapReduce 的"每一步写盘"改成"算完留在内存"，速度提升 10~100 倍；再用 SQL/DataFrame 把编程门槛砍掉，成为大数据批处理事实标准。

## 63.2 架构：Driver 调度 + Executor 执行

```
                        ┌────────────────────────────┐
                        │ Driver（应用主进程）          │
                        │  SparkContext：DAG 调度       │
                        │  Task 分发 / 资源申请 / 结果收集 │
                        └───────────┬────────────────┘
                                    │ 任务分发
                  ┌─────────────────┼──────────────────┐
                  │                 │                  │
         ┌────────▼───────┐ ┌──────▼────────┐ ┌───────▼───────┐
         │ Executor 1     │ │ Executor 2    │ │ Executor 3    │
         │  执行 Task      │ │  Task/缓存     │ │  Task         │
         │  BlockManager  │ │  BlockManager │ │  BlockManager │
         └────────────────┘ └───────────────┘ └───────────────┘
```

| 组件 | 职责 | 类比 |
| --- | --- | --- |
| **Driver** | SparkContext、DAG 调度、Task 分配 | 指挥官 |
| **Executor** | 执行 Task、内存缓存、Shuffle 管理 | 士兵（分布在多节点） |
| **Task** | 一个分区的计算单元 | 最小执行单元 |
| **资源** | Cluster Manager（YARN/K8s/Standalone）分配 | 战场 |

> **面试题**：Driver 挂了怎么办？答：Spark 2.x+ 支持 Driver 高可用（提交模式 cluster + recovery），由资源管理框架重启 Driver 并从上次状态恢复；Executor 挂了则自动重算其上的 Task（RDD 血统容错）。

## 63.3 核心抽象演进：RDD → DataFrame → Dataset

| 抽象 | 是什么 | 特点 |
| --- | --- | --- |
| **RDD** | 弹性分布式数据集（元素集合） | 最底层、函数式编程、粒度最细 |
| **DataFrame** | 有 Schema 的表（Row + 列名类型） | SQL 友好、Catalyst 优化、Java/Scala/Python 通用 |
| **Dataset** | 强类型 DataFrame（Java Bean） | 编译期类型安全 + 性能优化 |

```
演进本质：
RDD（Lambda 硬编码）→ DataFrame（SQL 声明式 + 自动优化）→ Dataset（类型安全）
生产现状：90% 用 DataFrame/SQL 写；RDD 只在自定义复杂逻辑时用
```

```java
// SparkSession 入口（所有 API 的统一入口）
SparkSession spark = SparkSession.builder()
        .appName("order-analysis")
        .master("yarn")
        .enableHiveSupport()          // 集成 Hive 元数据
        .getOrCreate();

// DataFrame：SQL 式处理（推荐）
Dataset<Row> orders = spark.read().json("hdfs://.../orders.json");
orders.createOrReplaceTempView("orders");

Dataset<Row> result = spark.sql(
    "SELECT user_id, SUM(amount) AS total FROM orders GROUP BY user_id ORDER BY total DESC");
result.show();
```

> **Catalyst 优化器**：Spark 会自动做谓词下推、列裁剪、常量折叠等优化——**你写 SQL 的"怎么写"远没有"数据怎么分布"重要**（63.7 数据倾斜才是大头）。

## 63.4 执行原理：DAG、宽窄依赖、Stage、Shuffle

```
算子链 → DAG（有向无环图）→ 划分 Stage（按 Shuffle 切分）→ Task 并行执行

窄依赖（map/filter）：父 RDD 1 分区 → 子 1 分区，可流水线（一个 Task 内连续做）
宽依赖（groupBy/reduceByKey）：父多分区 → 子分区（需 Shuffle 跨节点搬数据）
```

```
Stage 划分规则：遇到宽依赖（Shuffle）就切一个 Stage
Stage 1: map + filter（窄依赖，流水线） 
   │ Shuffle（按 key 跨节点搬运）
Stage 2: reduceByKey + map（窄依赖）
```

| 依赖 | 算子 | 是否跨节点 | 代价 |
| --- | --- | --- | --- |
| 窄依赖 | map/filter/union | 否（本地管道） | 快 |
| 宽依赖 | groupBy/reduceByKey/join | 是（Shuffle） | 慢（网络+磁盘） |

> **调优方向**：减少 Shuffle 次数 = 减少宽依赖 = 提速。能用 `reduceByKey`（预聚合）就不用 `groupByKey`（不预聚合、Shuffle 量大）。

## 63.5 Spark SQL 与 Hive：离线数仓的主力

```sql
-- 读 Hive 表做离线分析（T+1 的 DWS/ADS 就在这层算）
SELECT category_id, DATE(stat_date), SUM(amount) AS gmv
FROM dwd_order_detail          -- Hive 外表（HDFS 数据 + 元数据）
WHERE stat_date = '2026-08-23'
GROUP BY category_id, DATE(stat_date);

-- 结果写回 Hive 分区表
INSERT OVERWRITE TABLE dws_gmv_daily PARTITION (stat_date='2026-08-23')
SELECT category_id, SUM(amount) FROM dwd_order_detail GROUP BY category_id;
```

| 能力 | 说明 |
| --- | --- |
| Hive 集成 | 复用 Hive Metastore，Spark SQL 直接查 Hive 表 |
| 分区/分桶 | 分区裁剪省扫描、分桶优化 Join |
| 动态分区 | 写 Hive 自动建分区 |
| 与 62 章衔接 | 62 章实时 DWS + 本处离线 DWS，共用同一口径对账 |

> **离线数仓范式**：Hive 存（HDFS 表）+ Spark SQL 算（调度每日跑）+ 结果供报表/算法。实时部分见 62 章，两者对账闭环。

## 63.6 Structured Streaming：批流一体（微批模型）

```scala
// Structured Streaming：用 DataFrame API 写流（微批执行）
val lines = spark.readStream
  .format("kafka")
  .option("kafka.bootstrap.servers", "kafka:9092")
  .option("subscribe", "order-topic")
  .load()

val result = lines
  .selectExpr("CAST(value AS STRING) AS json")
  .select("json")
  .writeStream
  .outputMode("append")
  .format("console")
  .start()
```

| 维度 | Structured Streaming | Flink |
| --- | --- | --- |
| 模型 | 微批（攒小批处理） | 真流（逐条） |
| 延迟 | 秒级（微批间隔） | 毫秒级 |
| 容错 | exactly-once（WAL + 幂等） | exactly-once（Checkpoint 两阶段） |
| 心智 | 与批统一（同一套 DataFrame/SQL） | 流专属 + 批流一体（新版本） |

> **选型补充**：Spark 生态已有、延迟可接受 → Structured Streaming；延迟敏感/复杂流计算 → Flink（61 章）。**62 章实时数仓用 Flink 为主**，Spark 在"批流一体存量架构"中常见。

## 63.7 调优三板斧：数据倾斜 / Shuffle / 资源

### 63.7.1 数据倾斜（最常翻车的调优题）

```
现象：某个 key 数据量特别大（如大促某爆款商品），导致单个 Task 拖慢整个作业
诊断：Spark UI 看某 Task 耗时远高于其他 Task

三板斧：
① 两阶段聚合（加盐拆散再合并）：
   SELECT user_id, SUM(amount) FROM (
     SELECT user_id, IF(user_id = 热点值, CONCAT(user_id, '-', rand()%10), user_id) AS user_id,
            amount FROM orders
   ) GROUP BY user_id
   -- 热点 key 先加盐打散局部聚合，再去盐全局聚合
② 广播小表（join 倾斜）：
   小表 .broadcast() → 广播变量，避免 Shuffle join
③ 提高并行度 / 重新分区：spark.sql.shuffle.partitions
```

### 63.7.2 资源与缓存

| 优化 | 做法 |
| --- | --- |
| 动态资源 | `spark.dynamicAllocation.enabled=true` 按需申请 |
| 缓存复用 | `cache()` 重复使用的中间结果（注意清理 unpersist） |
| 序列化 | Kryo 序列化（比 Java 序列化小且快） |
| 并行度 | 分区数 ≈ CPU 核 × 2~3，避免小文件过多 |

## 63.8 Spark vs Flink 终极对比

| 维度 | Spark（Structured） | Flink |
| --- | --- | --- |
| 核心模型 | 微批（攒批） | 真流（逐条）+ 批流一体 |
| 延迟 | 秒级 | 毫秒级 |
| 批处理 | 强（事实标准） | 支持（批流统一） |
| 流处理 | 好（微批） | 强（事件时间/Watermark 原生） |
| 生态 | MLlib 机器学习强、SQL/Hive 无缝 | 实时数仓、复杂流计算强 |
| 适用 | 离线数仓、批流一体存量、ML | 实时数仓、毫秒级流处理 |

```
选型决策：
离线批处理 / T+1 数仓 / 机器学习 → Spark（首选）
毫秒级实时 / 复杂窗口状态流 → Flink（首选）
存量 Spark 团队要实时 → Structured Streaming（平滑过渡）
新项目实时数仓 → Flink SQL（62 章已验证）
```

> **一句话记忆**：Spark 是"批的王、流的客"，Flink 是"流的王、批也行"。离线选 Spark，实时选 Flink，团队存量决定过渡方案。

## 63.9 面试速查与本章小结

| 问题 | 一句话答案 |
| --- | --- |
| RDD/DataFrame/Dataset 区别？ | 底层函数式 → SQL 优化 → 强类型，生产用 DataFrame/SQL |
| Spark 为什么快？ | 内存计算 + DAG 调度 + Catalyst 优化 |
| 宽窄依赖区别？ | 窄依赖本地管道，宽依赖跨节点 Shuffle |
| 数据倾斜怎么解决？ | 两阶段聚合（加盐）/ 广播小表 / 调并行度 |
| Spark 和 Flink 怎么选？ | 批选 Spark，流选 Flink，存量生态决定过渡 |
| Spark SQL 与 Hive 关系？ | 复用 Hive 元数据，直接查 Hive 表（离线数仓主力） |

- **定位**（63.1）：Spark = 内存计算 + SQL 化，批处理事实标准；
- **架构**（63.2~63.4）：Driver 调度、Executor 执行、DAG 按宽依赖切 Stage、Shuffle 是主要代价；
- **编程**（63.3~63.6）：DataFrame/SQL 为主，Hive 集成做离线数仓，Structured Streaming 批流一体；
- **调优**（63.7~63.8）：数据倾斜三板斧，与 Flink 按场景分工（批 Spark、流 Flink）。

下一章：[64-ClickHouse与Doris分析引擎.md](./64-ClickHouse与Doris分析引擎.md)（秒级响应的 OLAP 分析引擎）｜ 返回：[README.md](./README.md)
