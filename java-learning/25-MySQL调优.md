# 第二十五章 MySQL 性能调优

> 本章目标：理解 MySQL 的索引结构与执行原理，掌握 `EXPLAIN` 分析、索引优化、SQL 优化、事务与锁、主从复制、分库分表等完整调优体系，能独立定位并解决线上慢查询问题。
>
> 前置知识：第六章 JDBC、第九章 Spring Boot + MyBatis、第十七章 MyBatis-Plus（数据层操作）。

## 25.1 为什么需要 MySQL 调优

### 25.1.1 慢查询的危害

```sql
-- 一条没走索引的 SQL，数据量 1000 万时：
SELECT * FROM orders WHERE user_phone = '138xxxx8888';
-- 全表扫描 1000 万行 ≈ 3~8 秒
-- 而走索引 ≈ 10 毫秒，差了 300 倍！
```

| 危害 | 表现 |
| --- | --- |
| 接口超时 | 一个慢 SQL 拖垮整个接口 |
| 拖垮数据库 | CPU/IO 被打满，所有业务一起慢 |
| 拖垮连接池 | 连接被慢查询占住，其他请求排队 |
| 引发雪崩 | 数据库假死 → 应用超时 → 连锁故障 |

### 25.1.2 调优金字塔（自上而下投入产出比递减）

```
┌─────────────────────────────┐
│ 1. SQL 与索引优化（成本最低）  │ ← 80% 的问题在这层解决
├─────────────────────────────┤
│ 2. 表结构设计（合理范式/字段） │
├─────────────────────────────┤
│ 3. 缓存（Redis，呼应十二章）   │
├─────────────────────────────┤
│ 4. 读写分离 / 分库分表         │ ← 数据量大了才上
├─────────────────────────────┤
│ 5. 硬件/参数（最后手段）       │
└─────────────────────────────┘
```

> 认知：**绝大多数慢查询是索引没建好或 SQL 写坏**，不是数据库配置问题。先优化 SQL，再谈架构。

## 25.2 索引原理（调优的地基）

### 25.2.1 为什么 MySQL 用 B+Tree

| 结构 | 查询 | 插入 | 特点 |
| --- | --- | --- | --- |
| 哈希表 | O(1) | O(1) | 不支持范围查询、排序 |
| 二叉树 | O(log n) | O(log n) | 数据多时树太高，IO 次数多 |
| 红黑树 | O(log n) | O(log n) | 依然太"高"，百万数据 20+ 层 |
| **B+Tree** | O(log n) | O(log n) | **矮胖**：三层可存千万数据，叶子链表支持范围查询 |

```
B+Tree（3 层可存约 2000 万条）：
┌─────────── 根节点（1 个页，存指针）──────────┐
│   [50]        [200]        [500]            │
└─────┬──────────┼────────────┼───────────────┘
      ▼          ▼            ▼
  [内部节点]   [内部节点]    [内部节点]   ← 每层更多指针
      ▼          ▼            ▼
  叶子节点 ──► 叶子节点 ──► 叶子节点 ──► 叶子节点   ← 双向链表
  (存完整记录或主键，按序排列，范围查询直接扫链表)
```

**B+Tree 三大特点（面试必问）**：
1. **矮胖**：节点存指针多（一个页 16KB 可存上千指针），3 层覆盖千万级数据，IO 只需 3 次
2. **叶子有序链表**：范围查询、排序、分页友好（`WHERE id > 100` 顺着链表扫）
3. **非叶子只存索引不存数据**：同样的内存能装更多索引，减少 IO

### 25.2.2 聚簇索引 vs 二级索引（面试必问）

```
聚簇索引（主键索引）：
  B+Tree 叶子节点 = 整行数据
  InnoDB 必须有且只有一个（主键；无主键则隐藏 rowid）

二级索引（普通索引）：
  B+Tree 叶子节点 = 索引列 + 主键值（不存整行）
  查询时：先找到主键 → 再回聚簇索引查整行 = 回表
```

```sql
CREATE TABLE user (
    id      BIGINT PRIMARY KEY,           -- 聚簇索引
    name    VARCHAR(50),
    phone   VARCHAR(20),
    INDEX idx_phone (phone)               -- 二级索引
);
-- SELECT * FROM user WHERE phone='138...'
-- 执行：二级索引查到 id → 回表查整行（2 次 IO 树查找）
-- SELECT id, phone FROM user WHERE phone='138...'  ← 覆盖索引，不用回表！
```

### 25.2.3 最左前缀原则（联合索引核心）

```sql
CREATE INDEX idx_name_age_phone ON user(name, age, phone);
-- 相当于建了三个索引：(name)、(name,age)、(name,age,phone)

-- ✅ 走索引：name / name,age / name,age,phone（按从左到右顺序）
SELECT * FROM user WHERE name='张三';
SELECT * FROM user WHERE name='张三' AND age=20;
SELECT * FROM user WHERE name='张三' AND age=20 AND phone='138';

-- ❌ 不走索引（跳过最左列）：
SELECT * FROM user WHERE age=20;                    -- 缺 name
SELECT * FROM user WHERE phone='138';               -- 缺 name
-- ⚠️ 部分失效（中间断列后失效）：
SELECT * FROM user WHERE name='张三' AND phone='138';  -- phone 用不上
```

> 联合索引排序规则：**先按第一列排，第一列相同再看第二列**，就像字典先按拼音再按声调。所以 `ORDER BY age` 单独用不上这个索引，`ORDER BY name, age` 才行。

## 25.3 EXPLAIN 执行计划（调优第一工具）

### 25.3.1 用法

```sql
EXPLAIN SELECT * FROM orders WHERE user_id = 100;
```

### 25.3.2 关键字段解读

| 字段 | 含义 | 重点关注 |
| --- | --- | --- |
| `type` | 访问类型 | **从好到坏**：`const` > `eq_ref` > `ref` > `range` > `index` > `ALL` |
| `key` | 实际使用的索引 | NULL = 没走索引（危险！） |
| `rows` | 预估扫描行数 | 越小越好 |
| `Extra` | 额外信息 | `Using filesort` / `Using temporary` / `Using index` |

```
EXPLAIN SELECT * FROM orders WHERE user_id = 100;
+----+-------------+--------+------+---------------+---------+------+-------+
| id | select_type | table  | type | possible_keys | key     | rows | Extra |
+----+-------------+--------+------+---------------+---------+------+-------+
|  1 | SIMPLE      | orders | ref  | idx_user_id   | idx_user_id | 12  | NULL |
+----+-------------+--------+------+---------------+---------+------+-------+
```

**判读标准**：
- `type=ALL` + `rows` 巨大 → **全表扫描，必须加索引**
- `Extra=Using filesort` → 排序没走索引，检查 `ORDER BY` 字段是否有索引
- `Extra=Using temporary` → 用了临时表（GROUP BY 常见），性能差
- `Extra=Using index` → 覆盖索引，**最佳状态**
- `key=NULL` → 索引失效或没建索引

## 25.4 索引失效的 8 种场景（高频考点）

```sql
-- 1. 对索引列做运算/函数
WHERE YEAR(create_time) = 2024      -- ❌ 应改为 create_time BETWEEN '2024-01-01' AND '2024-12-31'

-- 2. 隐式类型转换（字符 vs 数字）
WHERE phone = 13800138000            -- ❌ phone 是 varchar，数字会转字符串比较导致索引失效
--    应写 WHERE phone = '13800138000'

-- 3. 前导通配符
WHERE name LIKE '%张%'               -- ❌ 前面有 % 不走索引；'张%' 可以走

-- 4. 联合索引跳过最左列
WHERE age = 20                       -- ❌ 联合索引(name,age)必须带 name

-- 5. 使用 OR 且有一侧无索引
WHERE id = 1 OR phone = '138'        -- ❌ phone 无索引时整个 OR 不走索引
--    应改为 UNION ALL 或两边都建索引

-- 6. 对索引列进行 + - * /
WHERE price * 2 > 100                -- ❌ 应写 price > 50

-- 7. 使用 != / <> / NOT IN
WHERE status != 1                    -- ❌ 部分情况失效

-- 8. IS NULL / IS NOT NULL 与索引
WHERE name IS NOT NULL               -- ⚠️ 视优化器而定，尽量给默认值
```

**优化器放弃索引的其他原因**：
- 数据量很小（全表扫描更快）
- 命中比例过大（> 20% 时优化器认为扫全表更划算）
- 统计信息过期（`ANALYZE TABLE` 更新统计）

## 25.5 SQL 优化实战

### 25.5.1 分页深翻页（大 offset 慢）

```sql
-- ❌ offset 大时依然要扫描前面所有行
SELECT * FROM orders ORDER BY id LIMIT 1000000, 20;   -- 扫 100 万行

-- ✅ 方案一：延迟关联（先查 id 再 join）
SELECT o.* FROM orders o
JOIN (SELECT id FROM orders ORDER BY id LIMIT 1000000, 20) tmp
ON o.id = tmp.id;

-- ✅ 方案二：游标/上一页最大值（业务常用）
SELECT * FROM orders WHERE id > 1000020 ORDER BY id LIMIT 20;
```

### 25.5.2 避免 `SELECT *`

```sql
-- ❌ SELECT *：把不需要的列全部查出，加大 IO，且无法覆盖索引
-- ✅ 只查需要的列，配合覆盖索引
SELECT id, name FROM user WHERE phone = '138...';
```

### 25.5.3 COUNT 优化

```sql
-- ❌ COUNT(*) 对 InnoDB 也要全表扫描
-- ✅ 方案一：走二级索引（比主键索引小，扫描快）
SELECT COUNT(*) FROM orders;   -- 优化器一般会自动选最小索引

-- ✅ 方案二：用统计表/Redis 维护计数（准确 + 快速）
-- ✅ 方案三：information_schema.tables 的 rows（近似值，仅展示用）
```

### 25.5.4 大事务拆分

```java
// ❌ 一个事务处理 100 万条：长事务占用大量 undo log、锁，拖垮数据库
@Transactional
public void batchInsert(List<Order> list) {
    for (Order o : list) orderMapper.insert(o);
}

// ✅ 分批提交：每 1000 条一次，避免长事务
public void batchInsert(List<Order> list) {
    for (int i = 0; i < list.size(); i += 1000) {
        batchInsertInner(list.subList(i, Math.min(i + 1000, list.size())));
    }
}
```

### 25.5.5 常用优化速查表

| 场景 | 优化 |
| --- | --- |
| 大表深分页 | 延迟关联 / 游标分页 |
| 多表关联 | 小表驱动大表；关联字段建索引；避免 3 表以上 |
| `IN` 数量巨大 | 拆分批次或改 JOIN |
| `EXISTS` vs `IN` | 外层小表用 `IN`，内层小表用 `EXISTS`（新版 MySQL 差异已变小） |
| 大字段排序 | 别对 TEXT/BLOB 排序 |
| 插入性能 | 批量 `INSERT` 多行 / `LOAD DATA` |
| 唯一性校验 | 优先用唯一索引而非先查后插 |

## 25.6 事务与锁

### 25.6.1 隔离级别

| 隔离级别 | 脏读 | 不可重复读 | 幻读 |
| --- | --- | --- | --- |
| Read Uncommitted | 可能 | 可能 | 可能 |
| Read Committed（RC，默认） | 无 | 可能 | 可能 |
| Repeatable Read（RR，MySQL 默认） | 无 | 无 | **InnoDB 用 MVCC+间隙锁解决** |
| Serializable | 无 | 无 | 无 |

> **注意**：MySQL 默认 RR 但基本不会幻读（InnoDB 通过 MVCC + 间隙锁解决），而 Oracle 默认 RC。面试爱问"MySQL 为什么默认 RR 还能防幻读"。

### 25.6.2 MVCC（多版本并发控制）

```
版本链：每行数据有隐藏列（事务 ID、回滚指针）
  trx_id=101  │  trx_id=102  │  trx_id=103
   张三(新)   ──►  张三      ──►   李四(旧)

读已提交(RC)：每次 SELECT 生成新快照（看到最新已提交）
可重复读(RR)：事务内第一次 SELECT 生成快照（之后都看这个快照）
```

- **快照读**（普通 SELECT）：走 MVCC，不加锁，性能高
- **当前读**（`SELECT ... FOR UPDATE` / UPDATE / DELETE）：走最新数据，加锁

### 25.6.3 锁分类

```
按粒度：表锁 > 行锁（InnoDB 支持）
按模式：共享锁（S，读锁）/ 排他锁（X，写锁）
行锁细分：
  ┌ 记录锁（Record Lock）：锁单行
  ├ 间隙锁（Gap Lock）：锁区间，防幻读
  └ 临键锁（Next-Key Lock）：记录锁 + 间隙锁（RR 默认）
```

### 25.6.4 死锁排查

```sql
-- 查看死锁日志
SHOW ENGINE INNODB STATUS;
-- 自动检测：检测到死锁会回滚其中一个事务，报：
-- ERROR 1213 (40001): Deadlock found when trying to get lock
```

**死锁案例**：

```
事务 A：UPDATE t SET ... WHERE id=1;  UPDATE t SET ... WHERE id=2;
事务 B：UPDATE t SET ... WHERE id=2;  UPDATE t SET ... WHERE id=1;
```

**避免死锁**：
1. 多表/多行更新按**相同顺序**加锁
2. 一次 SQL 尽量少更新多行，缩小事务
3. 减少长事务，降低锁持有时间
4. 使用乐观锁（第十七章 `@Version`）替代行锁

## 25.7 慢查询定位与参数

### 25.7.1 开启慢查询日志

```sql
-- 查看当前配置
SHOW VARIABLES LIKE 'slow_query%';
SHOW VARIABLES LIKE 'long_query_time';

-- 临时开启（重启失效）
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 1;      -- 超过 1 秒的记录
SET GLOBAL log_queries_not_using_indexes = ON;  -- 没走索引的也记录
```

```bash
# 分析慢日志（mysqldumpslow 自带工具）
mysqldumpslow -s at -t 10 /var/lib/mysql/slow.log
# -s at：按平均耗时排序，取前 10 条
# 输出：Count: 500 Time=2.3s (1150s) ... 聚合后的慢 SQL
```

### 25.7.2 常用调优参数

```ini
# my.cnf
innodb_buffer_pool_size = 8G          # 最重要！InnoDB 缓存池，建议物理内存 50%~70%
innodb_buffer_pool_instances = 8      # 拆成多实例减少锁竞争
innodb_flush_log_at_trx_commit = 2    # 1=每次提交刷盘(最安全) 2=每秒刷盘(性能好)
sync_binlog = 1                       # 与上面组合：1+1 最安全，2+1 折中
max_connections = 500                 # 最大连接数（别盲目调大）
innodb_file_per_table = ON            # 每表独立表空间
```

> **排障顺序**：`EXPLAIN` 看 SQL → `SHOW PROCESSLIST` 看当前连接 → 慢日志看历史 → `SHOW ENGINE INNODB STATUS` 看锁/死锁 → 最后才调参数。

## 25.8 主从复制与读写分离

### 25.8.1 复制原理

```
主库(Master) ──写──► 主库 binlog
                        │ dump 线程
                        ▼
                   从库(Slave) I/O 线程 ──► 中继日志(relay log)
                                              │ SQL 线程
                                              ▼
                                          从库执行重放
```

- **异步复制**（默认）：主库提交后不等从库，主挂了可能丢数据
- **半同步复制**：等至少一个从库 ACK 才提交（可用性/一致性折中）
- **组复制(MGR)**：多主强一致（要求高可用场景）

### 25.8.2 配置主从

```ini
# 主库 my.cnf
server-id = 1
log-bin = mysql-bin
```

```sql
-- 主库：创建复制账号
CREATE USER 'repl'@'%' IDENTIFIED BY '123456';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
SHOW MASTER STATUS;   -- 记录 File 和 Position
```

```ini
# 从库 my.cnf
server-id = 2
```

```sql
-- 从库：指定主库
CHANGE MASTER TO
  MASTER_HOST='192.168.1.10', MASTER_USER='repl',
  MASTER_PASSWORD='123456',
  MASTER_LOG_FILE='mysql-bin.000001', MASTER_LOG_POS=1234;
START SLAVE;
SHOW SLAVE STATUS\G   -- 看 Slave_IO_Running / Slave_SQL_Running 是否都是 Yes
```

### 25.8.3 应用层读写分离

```java
// 简单实现：AOP 切面 + 注解选择数据源（生产用 ShardingSphere/MyCat）
@DataSource("read")    // 自定义注解标记走从库
public List<Order> queryOrders() { ... }

// 原则：读多写少场景，写走主库，读走从库
// 注意：主从有延迟，刚写完立刻读可能读不到（一致性敏感业务走主库）
```

## 25.9 分库分表

### 25.9.1 什么时候需要

| 数据量 | 手段 |
| --- | --- |
| 单表 < 500 万 | 索引优化即可 |
| 500 万 ~ 2000 万 | 加缓存 + 读写分离 |
| > 2000 万 或 单库压力大 | **分库分表** |

### 25.9.2 垂直 vs 水平

```
垂直分库：按业务拆库（用户库/订单库/商品库）
垂直分表：把宽表拆窄（大字段挪到扩展表）
水平分表：同一张表按规则拆成多张（orders_0、orders_1 ... orders_15）
```

### 25.9.3 分片策略

| 策略 | 规则 | 优点 | 缺点 |
| --- | --- | --- | --- |
| 范围分片 | `id % 16` 或日期范围 | 扩容简单 | 热点不均匀 |
| 哈希分片 | `user_id % 16` | 数据均匀 | 扩容要迁移 |
| 一致性哈希 | 环形哈希 | 扩容迁移少 | 复杂 |

```sql
-- 经典订单表拆分（按 user_id 哈希 16 张）
-- 路由：orders_${user_id % 16}
SELECT * FROM orders_3 WHERE user_id = 1003;   -- 1003 % 16 = 3
```

### 25.9.4 分库分表后的问题（面试重点）

| 问题 | 解决方案 |
| --- | --- |
| 全局唯一 ID | 雪花算法、Redis incr、美团 Leaf |
| 跨库 JOIN | 冗余字段 / 应用层组装 / 宽表（ES，呼应第二十章） |
| 跨分片分页排序 | 各分片查再归并排序（复杂度高） |
| 分布式事务 | 呼应第十五章 Seata：AT / TCC / MQ 最终一致 |
| 扩容数据迁移 | 停机迁移 / 双写迁移 / 影子表 |

> 工具：**ShardingSphere**（推荐，支持分片/读写分离/数据脱敏）、MyCat。**教训：分库分表一旦做了很难回头，数据量没到别硬上**。

## 25.10 小结与练习

**本章重点**：
- 调优顺序：SQL/索引 > 表结构 > 缓存 > 读写分离/分库分表 > 参数
- B+Tree：矮胖、叶子链表、非叶子只存索引
- 聚簇 vs 二级索引；回表；覆盖索引
- 联合索引**最左前缀**；8 种索引失效场景
- `EXPLAIN`：`type` 从好到坏 `const > ref > range > index > ALL`；`Using filesort` 是红灯
- 深分页：延迟关联 / 游标；避免 `SELECT *`
- MySQL 默认 RR + MVCC + 间隙锁防幻读
- 慢日志 + `mysqldumpslow`；参数 `innodb_buffer_pool_size`
- 主从复制：binlog → relay log 重放；读写分离注意延迟
- 分库分表：哈希/范围分片 + 全局 ID + 跨库难题

**面试题参考**：
1. 为什么 InnoDB 用 B+Tree 而不用 BTree / 红黑树 / 哈希？
2. 聚簇索引和二级索引有什么区别？什么是回表？
3. 联合索引的最左前缀原则是什么？举个例子。
4. 你遇到过哪些索引失效的场景？
5. 怎么分析一条慢 SQL？（EXPLAIN 各字段含义）
6. 大表分页很慢怎么优化？
7. MySQL 默认隔离级别是什么？为什么能防幻读？
8. MVCC 是怎么实现的？快照读和当前读的区别？
9. 主从复制原理？主库挂了会丢数据吗？
10. 什么情况下要分库分表？分完之后遇到哪些问题？

**课后练习**：
1. 造一张 100 万行的订单表，写几条慢 SQL，用 `EXPLAIN` 分析并加上索引前后对比耗时。
2. 练习 8 种索引失效场景，逐个验证 `EXPLAIN` 的 `type/key` 变化。
3. 开启慢查询日志，压测项目接口，用 `mysqldumpslow` 找出 Top 慢 SQL。
4. 用 Docker 起一主一从两个 MySQL，配置复制并在从库验证数据同步。
5. 模拟一个死锁（两个事务反向更新），用 `SHOW ENGINE INNODB STATUS` 分析死锁日志。

上一章：[24-JVM调优.md](./24-JVM调优.md) | 下一章：[26-Netty.md](./26-Netty.md) | 返回目录：[README.md](./README.md)
