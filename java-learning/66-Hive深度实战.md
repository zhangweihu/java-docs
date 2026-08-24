# 第六十六章 Hive 深度实战（离线数仓的存储与 SQL 引擎）

> 本章目标：第六十三章 Spark SQL 已经把 Hive 当作"离线数仓主力"，本章把 Hive 讲透——掌握 Hive 架构与 Metastore 元数据模型、表/分区/分桶的本质、文件格式与压缩怎么选、一条 Hive SQL 的执行流程、常见优化手段（分区裁剪/谓词下推/小文件治理/倾斜处理），并用"Java 后端视角"回答：Hive 到底在离线链路里干什么、怎么和 Spark/Flink 分工。
>
> 前置知识：第六十三章 Spark 体系（离线数仓）、第六十五章大数据平台全景（存储层）、第三十三章 MySQL（SQL 基础）、HDFS 概念。

## 66.1 Hive 是什么：把 HDFS 变成"能写 SQL 的仓库"

很多人第一次听到 Hive 会困惑：它不是数据库，却又能执行 SQL。一句话定位：

> **Hive 是构建在 HDFS 之上的数据仓库工具，把 SQL 翻译成 MapReduce/Spark 作业来跑。**

| 对比 | MySQL | Hive |
| --- | --- | --- |
| 定位 | OLTP 数据库 | 离线数仓工具 |
| 存储 | 本地/分布式文件，自带索引 | HDFS/对象存储，无索引 |
| 更新 | 支持行级增删改 | 一般只做追加写（批量覆盖分区） |
| 延迟 | 毫秒级 | 分钟~小时级（离线） |
| 数据量 | GB~TB | TB~PB |
| 计算引擎 | 自带 | MapReduce（老）/ **Tez / Spark**（主流） |

关键认知：**Hive 本身不存数据，也不算数据**。它只做三件事：

1. **元数据管理**：表结构、分区、文件位置，都记在 Metastore 里；
2. **SQL 翻译**：把 HiveQL 变成引擎任务（现在主流是 Spark/Tez）；
3. **结果读写**：按元数据找到 HDFS 上的文件，让引擎去读去算。

> **Java 后端视角**：Hive 之于离线数仓 ≈ MyBatis 之于数据库——它不存数据，只是把"你能描述的东西"翻译成底层引擎能跑的任务。

## 66.2 架构与 Metastore：Hive 的"数据库字典"

```
┌─────────────────────────────────────────────────┐
│ 客户端：Beeline / Hive CLI / JDBC（Java 程序）      │
└──────────────────────┬──────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────┐
│ HiveServer2（HS2）—— SQL 服务入口（编译/优化/执行计划）│
└───────────────┬─────────────────┬───────────────┘
                ▼                 ▼
┌────────────────────────┐  ┌────────────────────────┐
│  Metastore（元数据）     │  │  计算引擎（Tez/Spark/MR）│
│  MySQL 里存表结构/分区    │  │   └─ 读/写 HDFS 文件    │
└────────────────────────┘  └────────────────────────┘
```

**Metastore 是 Hive 的灵魂**——所有表结构、分区、字段、文件位置都存进 MySQL。这就是为什么 Spark SQL 只要 `enableHiveSupport()` 就能直接查 Hive 表（63 章）：**它复用同一份元数据**。

| Metastore 对象 | 存什么 | 类比 |
| --- | --- | --- |
| Database | 库名、路径 | MySQL 的库 |
| Table | 字段、存储格式、文件路径 | MySQL 的表 |
| Partition | 分区键取值 → 目录路径 | 一张表被拆成多个目录 |
| Statistics | 行数、文件大小 | 优化器用来做代价估算 |

> **面试点**：Spark 查 Hive 表为什么不用建表？因为 Hive Metastore 是"开放元数据层"，谁都可以通过标准接口（HMS API）拿表定义，Spark/Flink/Trino 都只是"用表的人"。

## 66.3 内部表 vs 外部表：删数据谁负责

| | 内部表（Managed） | 外部表（External） |
| --- | --- | --- |
| 建表 | 默认 | `CREATE EXTERNAL TABLE` |
| 数据位置 | Hive 管理目录（`/warehouse/xxx.db/表名`） | 你自己指定（`LOCATION '/data/xx'`） |
| DROP TABLE | **连数据一起删** | 只删元数据，文件还在 |
| 适用 | 数仓内部加工表 | 原始数据落地（日志、上游文件） |

> **铁律**：原始采集的数据一律用**外部表**（数据不是 Hive 的，删表不能删源数据）；加工结果表可以用内部表。误删原始数据的 90% 事故都源于"把外部表建成了内部表"。

## 66.4 分区与分桶：离线表的两把刀

### 分区（Partition）：按目录切，查询只扫需要的目录

```sql
-- 按天分区，这是离线数仓最基本的分区方式
CREATE TABLE dwd_order_detail (
  order_id    BIGINT,
  user_id     BIGINT,
  amount      DECIMAL(10,2),
  ...
)
PARTITIONED BY (dt STRING)   -- 分区键 dt
STORED AS PARQUET;

-- 写数据时指定分区
INSERT OVERWRITE TABLE dwd_order_detail PARTITION (dt='2026-08-23')
SELECT ... FROM ods_order WHERE create_time >= '2026-08-23 00:00:00'
                              AND create_time <  '2026-08-24 00:00:00';

-- 查询时只扫 8 月的 31 个目录，而不是全表
SELECT * FROM dwd_order_detail WHERE dt >= '2026-08-01' AND dt <= '2026-08-31';
```

物理上，每个分区就是一个目录：

```
/warehouse/dwd.db/dwd_order_detail/
  ├── dt=2026-08-01/  part-00000.parquet
  ├── dt=2026-08-02/  part-00000.parquet
  └── ...
```

> **分区裁剪**：`WHERE dt='...'` 会被优化器推下去，只读对应目录。**不带分区条件扫全表 = 离线 SQL 的第一大罪**。

### 分桶（Bucket）：按哈希切文件，Join/抽样提速

```sql
CREATE TABLE dws_user_order_bucket (
  user_id BIGINT, order_cnt BIGINT
)
CLUSTERED BY (user_id) INTO 64 BUCKETS   -- 按 user_id 哈希进 64 个文件
STORED AS PARQUET;
```

| 特性 | 分区 | 分桶 |
| --- | --- | --- |
| 切分方式 | 按列值目录切 | 按哈希值文件切 |
| 裁剪粒度 | 目录级 | 文件级 |
| 典型用途 | 按天/按地区过滤 | 大表 Join（桶表 Join 避免 Shuffle）、抽样 |
| 组合 | 先分区（天）→ 桶在分区目录内 | 每分区目录下 64 个桶文件 |

## 66.5 文件格式与压缩：离线表的"存储底料"

| 格式 | 存储 | 特点 | 适用 |
| --- | --- | --- | --- |
| TextFile | 行式、明文 | 通用、可读；占空间大、无压缩率 | 外部原始文件 |
| SequenceFile | 行式、二进制 | 可压缩、支持小文件合并 | 老 Hadoop 生态 |
| **ORC** | 列式 | 压缩率最高、内置索引、Hive 亲儿子 | Hive 内部表首选 |
| **Parquet** | 列式 | 压缩好、**跨引擎兼容性最佳**（Spark/Flink/Trino/Iceberg 都认） | 湖仓/多引擎场景首选 |

压缩选型（与格式叠加使用）：

| 压缩 | 特点 | 适用 |
| --- | --- | --- |
| Snappy | 压缩快、压缩率一般 | 计算密集、写多读少（默认） |
| Zstd | 压缩率与速度均衡 | 一般生产推荐 |
| Gzip | 压缩率高、慢 | 存储紧张、冷数据 |
| LZO | 支持切片 | 老场景 |

> **铁律**：**列式（ORC/Parquet）+ Snappy/Zstd** 是离线表的默认组合。同样的数据，TextFile 可能占 10TB，Parquet+Zstd 只要 2TB，扫描量差 5 倍。

## 66.6 一条 Hive SQL 的执行流程

```
SQL → ① 解析（AST）→ ② 语义分析（查 Metastore 校验字段/分区）
    → ③ 逻辑计划 → ④ 优化（分区裁剪/谓词下推/列裁剪）
    → ⑤ 物理计划（切成 Map/Reduce/Join 阶段）
    → ⑥ 提交引擎（Tez/Spark）→ ⑦ 读 HDFS 执行 → ⑧ 结果写回
```

以 `SELECT user_id, SUM(amount) FROM dwd_order_detail WHERE dt='2026-08-23' GROUP BY user_id;` 为例：

1. **解析**：语法检查，生成语法树；
2. **语义**：查 Metastore 确认表存在、字段对、`dt` 是分区键；
3. **优化**：`dt='2026-08-23'` → **分区裁剪**只读那一天；`user_id, amount` → **列裁剪**不读其他列；
4. **物理**：Map 阶段读出并按 user_id 预聚合 → Shuffle → Reduce 阶段汇总；
5. **执行**：Spark 引擎把计划变成 Job，跑在 YARN/K8s 上。

> **一句话**：Hive 查询优化的本质是"少读数据"——分区裁剪少读目录、列裁剪少读列、谓词下推少读行、压缩少读字节。

## 66.7 Hive SQL 实战：离线数仓的"加工车间"

这里用一段完整的离线加工链演示（衔接 62 章四层模型）：

```sql
-- ① ODS：原始日志外部表（来源 Flume/Filebeat 落 HDFS，外部表不动源数据）
CREATE EXTERNAL TABLE ods_order_log (
  order_id BIGINT, user_id BIGINT, amount DECIMAL(10,2),
  status STRING, event_time STRING
)
PARTITIONED BY (dt STRING)
ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.JsonSerDe'
LOCATION '/data/ods/order_log';

-- ② ODS → DWD：清洗 + 规范类型（只处理当天增量分区）
INSERT OVERWRITE TABLE dwd_order_detail PARTITION (dt='2026-08-23')
SELECT order_id, user_id, amount, status,
       CAST(event_time AS TIMESTAMP) AS create_time
FROM ods_order_log
WHERE dt = '2026-08-23'
  AND order_id IS NOT NULL          -- 清洗：去空
  AND amount > 0;                    -- 清洗：过滤异常

-- ③ DWS：按天聚合（离线 GMV 口径，与 62 章实时 DWS 对账）
INSERT OVERWRITE TABLE dws_gmv_daily PARTITION (dt='2026-08-23')
SELECT user_id, COUNT(*) AS order_cnt, SUM(amount) AS gmv
FROM dwd_order_detail
WHERE dt = '2026-08-23'
GROUP BY user_id;
```

> **离线数仓的口径真相**：`GROUP BY` + `INSERT OVERWRITE PARTITION` 就是离线的全部秘密——**每天把当天的分区"算一遍、覆盖一遍"**。调度器（65 章 DolphinScheduler）到点触发，产出 T+1 报表。

### 常用 HiveQL 技巧速查

```sql
-- 动态分区：不用手写每个 dt，按目标列自动建分区
SET hive.exec.dynamic.partition.mode = nonstrict;
INSERT OVERWRITE TABLE dws_order PARTITION (dt)
SELECT ..., dt FROM dwd_order_detail WHERE dt >= '2026-08-01';

-- 窗口函数：Top N / 同比环比（与 MySQL 8 语法一致，33 章可复用）
SELECT user_id, gmv,
       ROW_NUMBER() OVER (ORDER BY gmv DESC) AS rn
FROM dws_gmv_daily WHERE dt = '2026-08-23';

-- 侧视图 + 炸裂：处理数组/JSON 字段
SELECT user_id, tag
FROM dwd_user
LATERAL VIEW EXPLODE(split(tags, ',')) t AS tag;

-- 抽样：分桶表快速取样
SELECT * FROM dws_user_order_bucket TABLESAMPLE(BUCKET 1 OUT OF 64 ON user_id);
```

## 66.8 优化与调优：离线 SQL 慢在哪

### 性能瓶颈雷达（按优先级排查）

| 优先级 | 问题 | 症状 | 解法 |
| --- | --- | --- | --- |
| ① | 全表扫描 | 大表查询没写分区 | 补分区条件；默认禁止扫描分区表全表（`hive.mapred.mode`） |
| ② | **数据倾斜** | 个别 Reduce 跑几小时，其他秒完 | 见下文三板斧 |
| ③ | 小文件过多 | 任务启动慢、NameNode 压力大 | 合并小文件（见下文） |
| ④ | 无用 Shuffle | Join 大表无谓的落盘 | 用 Map Join（小表广播） |
| ⑤ | 压缩/格式差 | 存储大、扫描慢 | 换 Parquet/ORC + Snappy/Zstd |

### 数据倾斜三板斧（离线版）

```sql
-- ① 空值倾斜：把 NULL 键打散，最后再聚
SELECT IF(user_id IS NULL, CONCAT('rand_', RAND()), user_id) AS uid, COUNT(*)
FROM dwd_order GROUP BY IF(user_id IS NULL, CONCAT('rand_', RAND()), user_id);

-- ② 热点值（如头部用户）单独拎出来跑，再 union 回来
--   小表（维度表）直接广播：Map Join 无 Shuffle
SELECT /*+ MAPJOIN(dim_user) */ o.user_id, d.level
FROM dwd_order o JOIN dim_user d ON o.user_id = d.user_id;
```

> **小技巧**：倾斜本质是"少数 key 数据量巨大"。先定位（看 Stage 各 task 耗时差异），再对症——空值打散 / 热点拆分 / 广播小表，与 63 章 Spark 倾斜三板斧同源同理。

### 小文件治理

```sql
-- 合并小文件：先查目标分区再写回去（重写一遍自然合并）
INSERT OVERWRITE TABLE dwd_order_detail PARTITION (dt='2026-08-23')
SELECT * FROM dwd_order_detail WHERE dt = '2026-08-23';

-- 或开合并参数让写时自动合并
SET hive.merge.mapfiles = true;    -- 仅 Map 任务合并
SET hive.merge.size.per.task = 256000000;  -- 合并目标 256MB/文件
```

## 66.9 Hive / Spark / Flink 分工：离线引擎的"三国杀"

很多团队会有疑问：都有 Spark 了，还要 Hive 干什么？——**现代生产几乎总是 Spark + Hive 元数据一起用**（63 章 Spark SQL 就是跑在 Hive Metastore 之上）。真正的分工是这样的：

| 维度 | Hive（老引擎 Tez/MR） | Spark SQL（跑 Hive 表） | Flink SQL（实时） |
| --- | --- | --- | --- |
| 角色 | 数仓标准 + 元数据 + 老作业 | 离线计算主力引擎 | 实时计算 |
| 谁建表 | 元数据标准制定者 | 复用 Hive 元数据 | 独立/映射 Hive 表 |
| 场景 | 存量 SQL、老作业、团队标准 | 新离线作业、复杂 ETL、调优灵活 | 流式、实时数仓 |
| 性能 | 中（MR 时代产物） | 快（内存计算） | 快（流原生） |
| 何时用 | 团队已有 Hive 生态必须兼容 | **新离线任务默认 Spark SQL 跑 Hive 表** | 实时链路 |

> **一句话结论**：**Hive = 数仓的"标准 + 字典"，Spark = 离线算力的"发动机"**。生产上"Hive 建表存数据、Spark 算、DolphinScheduler 调度"是离线数仓的主流形态（65 章平台全景已验证）。

## 66.10 生产踩坑清单与面试速查

**踩坑清单**：

- [ ] 原始数据表误建成内部表，DROP 时把源数据删了（一律外部表）；
- [ ] 查询不带分区条件扫全表，10 亿行白跑 10 分钟（分区裁剪是命根）；
- [ ] 小文件堆积：每天 Flink 写 ODS 产生几千个小文件，任务越来越慢（定期合并）；
- [ ] 格式用了 TextFile 且不压缩，存储和扫描都贵 5 倍（列式 + Snappy/Zstd）；
- [ ] 空值/热点导致 Reduce 倾斜，一个任务跑半天（三板斧对症）；
- [ ] 动态分区开了 `nonstrict` 后误写几万个分区目录（慎用、限分区数）；
- [ ] 字段类型不一致：ODS 存 STRING，DWD 忘了 CAST，聚合结果千奇百怪（清洗层统一类型）。

**面试速查**：

| 问题 | 一句话答案 |
| --- | --- |
| Hive 是什么？ | HDFS 上的数据仓库工具，SQL 翻译成 MapReduce/Tez/Spark 作业 |
| 内部表 vs 外部表？ | 删表删不删数据；原始数据用外部表，加工表用内部表 |
| 分区 vs 分桶？ | 分区按值切目录省扫描；分桶按哈希切文件优化 Join/抽样 |
| 为什么查 Hive 快？ | 分区裁剪少读目录 + 列裁剪少读列 + 谓词下推少读行 + 压缩少读字节 |
| 文件格式选什么？ | 列式 Parquet/ORC + Snappy/Zstd；跨引擎用 Parquet，Hive 内部用 ORC |
| 数据倾斜怎么办？ | 定位热点 → 空值打散 / 热点拆分 / 小表广播（Map Join） |
| Hive 和 Spark 关系？ | Hive 管元数据与标准，Spark SQL 做离线算力，共用 Metastore |

### 本章小结

- **定位**（66.1~66.2）：Hive 不存不算，只管元数据 + SQL 翻译；Metastore 是开放字典；
- **建模**（66.3~66.5）：外部表保原始、分区省扫描、分桶省 Shuffle，列式 + 压缩是默认组合；
- **执行**（66.6~66.7）：优化本质是"少读数据"，INSERT OVERWRITE 分区即离线加工范式；
- **调优**（66.8~66.9）：分区裁剪 / 倾斜三板斧 / 小文件治理；与 Spark 分工"Hive 建表、Spark 算"。

至此，第 66 章 Hive 深度实战学习完成。下一章 [67-数据湖Iceberg实战.md](./67-数据湖Iceberg实战.md)（把 Hive 表升级成 ACID 数据湖）｜ 返回：[README.md](./README.md)
