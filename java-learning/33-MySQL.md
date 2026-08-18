# 第三十三章 MySQL 系统学习（从 SQL 基础到架构演进）

> 本章目标：系统掌握 MySQL——从建库建表、增删改查的 SQL 基础，到索引、事务、锁等核心机制，再到主从复制、读写分离、分库分表等生产架构。可与第二十五章《MySQL 性能调优》配套阅读：25 章讲"调"，本章讲"全"。
>
> 前置知识：第六章 JDBC、第九章 Spring Boot + MyBatis、第十七章 MyBatis-Plus。

## 33.1 MySQL 概述

### 33.1.1 关系型数据库（RDBMS）

| 概念 | 说明 |
| --- | --- |
| **数据库** | 存储数据的仓库，本质是文件的集合 |
| **表** | 数据的二维结构：行（记录）+ 列（字段） |
| **SQL** | 结构化查询语言，操作数据库的标准语言 |
| **关系** | 表与表之间通过外键/主键建立的联系 |

```sql
-- 以用户表为例：一张表 = 一个业务实体
-- 行 = 一条用户记录；列 = 用户的属性（id、name、phone）
SELECT id, name, phone FROM t_user WHERE id = 1;
```

常见数据库对比：

| 数据库 | 类型 | 特点 |
| --- | --- | --- |
| **MySQL** | 关系型 | 开源免费、中小型首选、互联网标配 |
| Oracle | 关系型 | 功能强大、贵、银行/大厂传统系统 |
| PostgreSQL | 关系型 | 功能最接近 Oracle、JSON 支持好 |
| SQL Server | 关系型 | 微软系、Windows 生态 |

### 33.1.2 安装与连接

**方式一：Docker 一键启动（推荐，本章示例均基于 MySQL 8.0）**

```bash
docker run -d --name mysql8 \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root123 \
  mysql:8.0
```

**方式二：Linux（CentOS/Ubuntu）**

```bash
sudo apt install mysql-server -y      # Ubuntu
sudo systemctl start mysqld           # 启动
sudo mysql                            # 免密进入（root）
```

**连接客户端**

```bash
mysql -uroot -p                       # 本地连接，回车后输入密码
mysql -h 192.168.1.10 -P 3306 -uroot -p   # 远程连接
```

### 33.1.3 SQL 分类（五大类）

| 分类 | 全称 | 作用 | 关键字 |
| --- | --- | --- | --- |
| **DDL** | 数据定义语言 | 操作库/表结构 | CREATE、ALTER、DROP、TRUNCATE |
| **DML** | 数据操作语言 | 操作表中数据 | INSERT、UPDATE、DELETE |
| **DQL** | 数据查询语言 | 查询数据 | SELECT |
| **DCL** | 数据控制语言 | 权限控制 | GRANT、REVOKE |
| **TCL** | 事务控制语言 | 事务管理 | COMMIT、ROLLBACK、SAVEPOINT |

> **学习顺序**：DDL（建表）→ DML（写数据）→ DQL（查数据）→ 索引/事务（进阶）→ 架构（生产）。

## 33.2 库表操作（DDL）

### 33.2.1 数据库操作

```sql
-- 查看/创建/使用/删除
SHOW DATABASES;                                   -- 查看所有库
CREATE DATABASE mall DEFAULT CHARSET utf8mb4;     -- 建库（utf8mb4 支持 emoji）
USE mall;                                         -- 切换库
DROP DATABASE mall;                               -- 删库（慎用！生产环境禁用）
```

> 为什么用 **utf8mb4** 而不是 utf8？utf8mb4 是 utf8 的超集，能存 4 字节字符（emoji、生僻字），是 MySQL 8.0 默认字符集。

### 33.2.2 数据类型（常用）

| 类型 | 说明 | 示例 |
| --- | --- | --- |
| INT / BIGINT | 整数（INT 4 字节、BIGINT 8 字节） | 年龄、ID |
| DECIMAL(M,D) | 精确小数（金额**必须用**） | DECIMAL(10,2) |
| FLOAT / DOUBLE | 浮点（有精度误差） | 评分、坐标 |
| VARCHAR(n) | 变长字符串 | 用户名、手机号 |
| CHAR(n) | 定长字符串 | 性别、状态码 |
| TEXT | 大文本 | 文章内容 |
| DATE / TIME | 日期 / 时间 | 生日 |
| DATETIME | 日期时间（范围大，推荐） | 创建时间 |
| TIMESTAMP | 时间戳（受时区影响，2038 年问题） | 更新时间 |

> **金额为什么用 DECIMAL？** `0.1 + 0.2` 在浮点中等于 `0.30000000000000004`，DECIMAL 按字符串精确存储，杜绝金额误差。

### 33.2.3 约束（5 种 + 自增）

| 约束 | 关键字 | 作用 |
| --- | --- | --- |
| 主键 | PRIMARY KEY | 唯一标识一行，非空 + 唯一 |
| 非空 | NOT NULL | 字段不能为空 |
| 唯一 | UNIQUE | 字段值不能重复 |
| 默认 | DEFAULT | 未传值时用默认值 |
| 外键 | FOREIGN KEY | 表关联完整性（**生产一般不建**，靠应用层控制） |
| 自增 | AUTO_INCREMENT | 整数自动 +1 |

### 33.2.4 建表/改表/删表

```sql
-- 建表：t_user（第 31 章商城用户表的简化版）
CREATE TABLE t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NOT NULL COMMENT '用户名',
    password    VARCHAR(128) NOT NULL COMMENT '密码(MD5/BCrypt)',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 改表
ALTER TABLE t_user ADD COLUMN email VARCHAR(64) DEFAULT NULL COMMENT '邮箱';   -- 加列
ALTER TABLE t_user MODIFY COLUMN phone VARCHAR(20) NOT NULL;                  -- 改类型
ALTER TABLE t_user CHANGE COLUMN phone mobile VARCHAR(20);                    -- 改列名
ALTER TABLE t_user DROP COLUMN email;                                         -- 删列

-- 删表
DROP TABLE t_user;
```

## 33.3 数据操作（DML）

```sql
-- 新增：单条 / 批量（批量性能远优于循环单条）
INSERT INTO t_user (username, password, phone) VALUES ('zhangsan', '123456', '13800001111');
INSERT INTO t_user (username, password) VALUES
  ('lisi', '123456'), ('wangwu', '123456'), ('zhaoliu', '123456');

-- 修改（务必带 WHERE！）
UPDATE t_user SET phone = '13900002222' WHERE id = 1;
UPDATE t_user SET status = 1 WHERE status IS NULL;   -- 批量更新

-- 删除
DELETE FROM t_user WHERE id = 100;        -- 删行（可回滚）
TRUNCATE TABLE t_user;                    -- 清空表（不可回滚、重置自增、快）
DROP TABLE t_user;                        -- 连表结构一起删
```

> **DELETE vs TRUNCATE vs DROP**：DELETE 逐行删可回滚、有 DML 日志；TRUNCATE 直接重建表段、不可回滚、快；DROP 删表结构。生产删除数据用 DELETE 并做好备份，TRUNCATE/DROP 需审批。

## 33.4 单表查询（DQL 重点）

### 33.4.1 基础与条件查询

```sql
-- 基础
SELECT * FROM t_user;                                  -- 全查（开发可、生产避免）
SELECT id, username, phone FROM t_user;                -- 指定列
SELECT DISTINCT status FROM t_user;                    -- 去重
SELECT username AS 姓名, phone AS 手机 FROM t_user;     -- 别名（可省略 AS）

-- 条件：比较 / 逻辑 / 模糊 / 范围 / 空值
SELECT * FROM t_user WHERE id > 5;                     -- 比较 > < >= <= != 
SELECT * FROM t_user WHERE status = 1 AND phone IS NOT NULL;  -- 逻辑 AND OR NOT
SELECT * FROM t_user WHERE username LIKE 'zhang%';     -- 模糊：%任意多个 _单个
SELECT * FROM t_user WHERE id IN (1, 3, 5);            -- IN 列表
SELECT * FROM t_user WHERE id BETWEEN 1 AND 10;        -- 范围（闭区间）
SELECT * FROM t_user WHERE phone IS NULL;              -- 空值判断（不是 = NULL！）
```

### 33.4.2 排序与分页

```sql
SELECT * FROM t_user ORDER BY create_time DESC;        -- 倒序（DESC）/ 正序（ASC 默认）
SELECT * FROM t_user ORDER BY status ASC, id DESC;     -- 多字段：先 status 再 id
SELECT * FROM t_user LIMIT 10;                         -- 取前 10 条
SELECT * FROM t_user LIMIT 20, 10;                     -- 跳过 20 条取 10 条（第 3 页，每页 10）
-- 分页公式：LIMIT (pageNo-1)*pageSize, pageSize
```

### 33.4.3 聚合函数与分组

```sql
-- 聚合（忽略 NULL）
SELECT COUNT(*) FROM t_user;               -- 总行数
SELECT COUNT(phone) FROM t_user;           -- phone 非空数量
SELECT SUM(price), AVG(price), MAX(price), MIN(price) FROM t_order_item;

-- 分组 + 条件过滤（WHERE 过滤行，HAVING 过滤组）
SELECT status, COUNT(*) AS cnt FROM t_user GROUP BY status;
SELECT status, COUNT(*) AS cnt FROM t_user GROUP BY status HAVING cnt > 1;
```

### 33.4.4 SQL 执行顺序（面试必问）

```sql
SELECT   ← 5. 选择列（可起别名）
  ...
FROM     ← 1. 确定来源表
  ...
WHERE    ← 2. 逐行过滤（此时不能用别名！）
  ...
GROUP BY ← 3. 分组
  ...
HAVING   ← 4. 过滤分组（此时能用别名）
  ...
ORDER BY ← 6. 排序
LIMIT    ← 7. 分页
```

> 记忆：`FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT`。因为 WHERE 在 SELECT 之前执行，所以 WHERE 里不能使用 SELECT 起的别名。

## 33.5 多表查询

### 33.5.1 连接查询（重点）

```sql
-- 内连接：只返回两表匹配的行（交集）
SELECT u.username, o.order_no
FROM t_user u
INNER JOIN t_order o ON u.id = o.user_id;

-- 左外连接：左表全部保留，右表无匹配为 NULL
SELECT u.username, o.order_no
FROM t_user u
LEFT JOIN t_order o ON u.id = o.user_id;   -- 统计每个用户买了什么，没买的也为 NULL 行

-- 右外连接：右表全部保留
SELECT u.username, o.order_no
FROM t_user u
RIGHT JOIN t_order o ON u.id = o.user_id;

-- 自连接：一张表自己连自己（如员工-上级）
SELECT e.name AS 员工, m.name AS 上级
FROM t_emp e
LEFT JOIN t_emp m ON e.manager_id = m.id;
```

### 33.5.2 子查询

```sql
-- 标量子查询（返回单值）
SELECT * FROM t_order WHERE user_id = (SELECT id FROM t_user WHERE username = 'zhangsan');

-- 列子查询（IN）
SELECT * FROM t_order WHERE user_id IN (SELECT id FROM t_user WHERE status = 0);

-- 表子查询（当成临时表用）
SELECT u.username, t.total
FROM (SELECT user_id, SUM(amount) AS total FROM t_order GROUP BY user_id) t
JOIN t_user u ON u.id = t.user_id;

-- EXISTS：是否存在（比 IN 更高效的大数据量场景）
SELECT * FROM t_user u WHERE EXISTS (SELECT 1 FROM t_order o WHERE o.user_id = u.id);

-- UNION 合并（去重）/ UNION ALL（不去重，更快）
SELECT username FROM t_user WHERE status = 1
UNION ALL
SELECT username FROM t_user WHERE status = 0;
```

> **IN vs EXISTS**：子查询结果集小用 IN，外表大/子表大用 EXISTS；实际优化器会做等价改写，生产以 EXPLAIN 为准。

## 33.6 视图与索引

### 33.6.1 视图（View）

视图是一张**虚拟表**：SQL 保存起来、不存数据，每次查询实时执行。用于权限隔离、简化复杂查询。

```sql
-- 创建视图：隐藏敏感字段，简化联表
CREATE VIEW v_order_detail AS
SELECT u.username, o.order_no, o.amount
FROM t_user u JOIN t_order o ON u.id = o.user_id;

SELECT * FROM v_order_detail WHERE amount > 100;   -- 像查表一样用
DROP VIEW v_order_detail;
```

### 33.6.2 索引（重点）

**索引 = 书的目录**：没有目录要翻整本书，有了目录直接定位。MySQL 默认用 **B+Tree**（详见 25 章）。

```sql
-- 创建索引（三种）
CREATE INDEX idx_phone ON t_user(phone);              -- 普通索引
CREATE UNIQUE INDEX uk_phone ON t_user(phone);        -- 唯一索引
CREATE INDEX idx_user_status ON t_user(username, status);  -- 复合索引（左前缀原则）

-- 查看 / 删除
SHOW INDEX FROM t_user;
DROP INDEX idx_phone ON t_user;
```

**复合索引最左前缀原则**：`idx(username, status)` 能命中 `username`、`username+status`，但不能单独命中 `status`。建索引时把最常用的列放最左。

**索引失效场景（面试必考）**：

| 场景 | 示例（失效） |
| --- | --- |
| 函数/运算 | `WHERE YEAR(create_time) = 2025` |
| 隐式类型转换 | `WHERE phone = 13800001111`（phone 是 varchar） |
| LIKE 前置通配符 | `WHERE username LIKE '%zhang'` |
| OR 连接非索引列 | `WHERE id = 1 OR status = 0` |
| 违反最左前缀 | `WHERE status = 1`（复合索引首列非 username） |
| 负向查询 | `!=`、`NOT IN`、`IS NOT NULL` 慎用 |

> **不是索引越多越好**：每个索引都是额外的 B+Tree，写入要维护、占磁盘。单表建议 5 个以内。

## 33.7 事务与隔离级别（核心）

### 33.7.1 事务的 ACID

| 特性 | 含义 | 类比 |
| --- | --- | --- |
| **A** 原子性 | 要么全成功要么全失败 | 转账：转出+转入一条龙 |
| **C** 一致性 | 事务前后数据完整性不被破坏 | 余额总和不变 |
| **I** 隔离性 | 事务间互不干扰 | 各干各的 |
| **D** 持久性 | 提交后永久保存 | 落盘不丢 |

```sql
-- 转账示例：两步必须一起成功或一起失败
START TRANSACTION;                              -- 开启事务（或 BEGIN）
UPDATE t_account SET balance = balance - 100 WHERE user_id = 1;
UPDATE t_account SET balance = balance + 100 WHERE user_id = 2;
COMMIT;                                         -- 提交：持久化
-- 任一步失败则执行 ROLLBACK; 回滚全部
```

### 33.7.2 隔离级别与并发问题

| 并发问题 | 现象 | 解决级别 |
| --- | --- | --- |
| **脏读** | 读到别的事务**未提交**的数据 | 读已提交起 |
| **不可重复读** | 同一行数据两次读**结果不同**（被修改） | 可重复读起 |
| **幻读** | 同一范围两次查**行数不同**（被插入） | 串行化 |

| 隔离级别 | 脏读 | 不可重复读 | 幻读 |
| --- | --- | --- | --- |
| 读未提交 READ UNCOMMITTED | 可能 | 可能 | 可能 |
| 读已提交 READ COMMITTED | 否 | 可能 | 可能 |
| **可重复读 REPEATABLE READ（MySQL 默认）** | 否 | 否 | 可能（InnoDB 用间隙锁+MVCC 已基本解决） |
| 串行化 SERIALIZABLE | 否 | 否 | 否（性能最差） |

```sql
SELECT @@transaction_isolation;               -- 查看当前隔离级别（8.0）
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;  -- 会话级修改
```

> MySQL 默认**可重复读**；Oracle 默认读已提交。MySQL 通过 **MVCC（多版本并发控制）** 实现快照读：每行有隐藏版本号，读操作读快照，写操作加锁，读不加锁、读写不互斥，性能极高。

## 33.8 锁机制

| 锁 | 粒度 | 说明 |
| --- | --- | --- |
| 全局锁 | 整个库 | `FLUSH TABLES WITH READ LOCK`（备份用，阻塞所有写） |
| 表锁 | 整张表 | MyISAM 默认；`LOCK TABLES ... READ/WRITE` |
| 行锁 | 单行 | **InnoDB 默认**，只锁命中的行，并发高 |
| 间隙锁 | 区间 | 防止幻读，锁住范围不允许插入 |
| 临键锁 | 行+间隙 | 行锁 + 间隙锁组合 |

**乐观锁 vs 悲观锁（面试高频）**：

```sql
-- 悲观锁：查出来就锁住，别人读不到（SELECT ... FOR UPDATE）
-- 适用于：并发不高、冲突频繁（如订单金额修改）
BEGIN;
SELECT * FROM t_account WHERE user_id = 1 FOR UPDATE;   -- 锁住该行
UPDATE t_account SET balance = balance - 100 WHERE user_id = 1;
COMMIT;

-- 乐观锁：不锁，更新时比对版本号（CAS 思想）
-- 适用于：高并发、冲突少（如第 31 章库存扣减）
-- 更新时带上版本号条件，影响行数为 0 说明被别人改了，重试
UPDATE t_product SET stock = stock - 1, version = version + 1
WHERE id = 1 AND version = 5;
```

**死锁**：两个事务互相持有对方需要的锁。解决：业务上统一加锁顺序（先 A 后 B）、超时回滚（`innodb_lock_wait_timeout`）、死锁检测。MySQL 会自动检测并回滚代价小的一方，应用层捕获 `Deadlock found` 重试即可。

## 33.9 存储引擎

| 对比 | **InnoDB（默认，8.0 唯一内置）** | MyISAM（旧） |
| --- | --- | --- |
| 事务 | 支持 | 不支持 |
| 行级锁 | 支持 | 只支持表锁 |
| 外键 | 支持 | 不支持 |
| 崩溃恢复 | 支持（redo log） | 不支持 |
| 索引结构 | B+Tree，数据在索引叶子 | B+Tree，数据在文件 |
| 适用 | **互联网 OLTP 业务** | 只读仓库、全文索引旧场景 |

```sql
-- 查看引擎
SHOW ENGINES;
SHOW TABLE STATUS LIKE 't_user';   -- 查看表使用的引擎
```

## 33.10 性能优化实战（提炼，详见第 25 章）

### 33.10.1 EXPLAIN 分析慢 SQL

```sql
EXPLAIN SELECT * FROM t_order WHERE user_id = 100 AND amount > 500;
```

重点看三列：

| 列 | 含义 | 好/坏 |
| --- | --- | --- |
| type | 访问类型 | `system > const > eq_ref > ref > range > index > ALL`（ALL 全表扫描最差） |
| key | 实际使用的索引 | NULL = 没走索引 |
| rows | 预估扫描行数 | 越小越好 |

### 33.10.2 慢查询日志

```sql
-- my.cnf 配置（或 SET GLOBAL 临时开启）
slow_query_log = ON
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 1          -- 超过 1 秒记录
```

### 33.10.3 SQL 优化口诀

```
少用 SELECT *、避免隐式转换、LIKE 不前置 %
WHERE 中的计算移到代码层、OR 改 UNION ALL、深分页用游标/覆盖索引
大事务拆小、长事务提前提交、索引覆盖查询避免回表
```

## 33.11 主从复制与读写分离

### 33.11.1 为什么需要主从

1. **读写分离**：读请求多（90%+），从库分担读压力；
2. **高可用**：主库挂了从库顶上；
3. **灾备**：多节点冗余。

### 33.11.2 复制原理（binlog）

```
写请求 → 主库(写入后记 binlog)
              │  dump 线程推送
              ▼
          从库 IO 线程 → 写入中继日志 relay log
              │  SQL 线程
              ▼
           从库回放，数据一致
```

```sql
-- 主库配置（my.cnf）
server-id = 1
log-bin = mysql-bin

-- 从库配置
server-id = 2

-- 从库执行：告诉主库"我从哪里同步"
CHANGE MASTER TO
  MASTER_HOST = '192.168.1.10',
  MASTER_USER = 'repl',
  MASTER_PASSWORD = 'repl123',
  MASTER_LOG_FILE = 'mysql-bin.000001',
  MASTER_LOG_POS = 154;
START SLAVE;
SHOW SLAVE STATUS\G;   -- 看 Slave_IO_Running / Slave_SQL_Running 是否双 Yes
```

> 三种复制模式：**异步**（默认，可能丢数据）、**半同步**（主等一个从确认，5.7+ 支持）、**组复制 MGR**（强一致）。

### 33.11.3 读写分离落地

应用层方案：**ShardingSphere-JDBC**（客户端路由，代码侵入小）或 **MyCat/ProxySQL**（代理层）。配置示例（ShardingSphere）：

```yaml
rules:
  - !READWRITE_SPLITTING
    dataSources:
      readwrite_ds:
        writeDataSourceName: ds_master
        readDataSourceNames: [ds_slave_0, ds_slave_1]
        loadBalancerName: round_robin
```

## 33.12 分库分表

当单库数据量到千万级、写入压力大时，需要拆分：

### 33.12.1 拆分方式

| 方式 | 说明 | 举例 |
| --- | --- | --- |
| **垂直分库** | 按业务拆到不同库 | 用户库、订单库、商品库（第 32 章商城即如此） |
| **垂直分表** | 大表拆字段 | 商品主表 + 商品详情表 |
| **水平分库** | 同一张表按规则散到多库 | order_0 ~ order_15 |
| **水平分表** | 同一张表散到多表 | order_0 ~ order_15（每库内再分） |

### 33.12.2 分片策略

| 策略 | 规则 | 优缺点 |
| --- | --- | --- |
| 取模 hash | `shard = user_id % 16` | 分布均匀；扩容要迁移数据 |
| 范围 range | 按时间/ID 区间 | 扩容简单；热点不均 |
| 一致性哈希 | 哈希环 | 扩容影响小；实现复杂 |

### 33.12.3 分库分表带来的问题

| 问题 | 解决思路 |
| --- | --- |
| 分布式 ID | 雪花算法（Snowflake）、号段模式（Leaf） |
| 跨库 join | 应用层组装、宽表冗余、异构数据（ES） |
| 跨库事务 | Seata AT 模式（呼应第 32 章） |
| 分布式主键唯一 | 全局 ID 生成器统一发放 |
| 排序分页 | 各片取 N 再合并、数据归档 |

> **何时才需要分库分表**：单表超过 2000 万、或单库写入/连接成为瓶颈时。**优先**做缓存（第 34 章）、索引优化、读写分离，最后才分库分表。

## 33.13 Spring Boot + MyBatis 实战

### 33.13.1 依赖与配置

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mall?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root123
    hikari:                          # 连接池（Spring Boot 默认 HikariCP，性能最强）
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

### 33.13.2 注解式 SQL

```java
@Mapper
public interface UserMapper {

    // 参数绑定：#{xx} 预编译防 SQL 注入（绝不能拼字符串！）
    @Select("SELECT * FROM t_user WHERE username = #{username}")
    User findByUsername(String username);

    @Insert("INSERT INTO t_user(username, password, phone) VALUES(#{username}, #{password}, #{phone})")
    @Options(useGeneratedKeys = true, keyProperty = "id")   // 回填自增主键
    int insert(User user);

    @Update("UPDATE t_user SET phone = #{phone} WHERE id = #{id}")
    int updatePhone(@Param("id") Long id, @Param("phone") String phone);

    @Delete("DELETE FROM t_user WHERE id = #{id}")
    int deleteById(Long id);
}
```

> **SQL 注入防护**：`#{}` 会生成预编译 `?` 占位符，绝对安全；`${}` 直接拼接有注入风险，仅用于动态表名/列名等少数场景。

## 33.14 练习与总结

### 练习题（动手敲）

```sql
-- 1. 建一张 t_order 表：id 主键自增、order_no 唯一、user_id、amount DECIMAL(10,2)、status、create_time
-- 2. 批量插入 20 条订单数据
-- 3. 查询每个用户的总消费金额，按金额倒序，只取前 5（JOIN + GROUP BY + ORDER BY + LIMIT）
-- 4. 用 EXPLAIN 分析上面的 SQL，确认走了哪些索引
-- 5. 开启事务：模拟"下单 = 扣库存 + 减余额"，全部成功才 COMMIT
-- 6. 为 t_order 建 (user_id, status) 复合索引，验证最左前缀
```

### 面试高频问题清单

| 问题 | 答案要点 |
| --- | --- |
| SQL 执行顺序？ | FROM→WHERE→GROUP BY→HAVING→SELECT→ORDER BY→LIMIT |
| InnoDB 为什么用 B+Tree？ | 矮胖少 IO、叶子链表范围查询、数据聚簇 |
| 索引失效场景？ | 函数、隐式转换、LIKE 前置 %、OR、违反最左前缀 |
| 事务四大特性？ | ACID：原子性、一致性、隔离性、持久性 |
| MVCC 是什么？ | 快照读 + 版本链 + undo log，读写不互斥 |
| 主从复制原理？ | binlog → IO 线程拉取 → relay log → SQL 线程回放 |
| 乐观锁与悲观锁区别？ | 版本号 CAS vs SELECT FOR UPDATE |
| 什么时候分库分表？ | 缓存→索引→读写分离之后，单表 2000 万+ |

### 本章小结

- **SQL 基础**（33.2~33.5）：DDL/DML/DQL + 连接查询 + 子查询，能独立写业务查询；
- **核心机制**（33.6~33.9）：索引 B+Tree、事务 ACID 与隔离级别、MVCC、锁、存储引擎；
- **生产架构**（33.10~33.12）：EXPLAIN 优化、主从复制、读写分离、分库分表；
- **工程落地**（33.13）：Spring Boot + MyBatis 注解 SQL + 连接池 + 预编译防注入。

配套：第 25 章《MySQL 性能调优》深入调优；第 31/32 章商城项目是 MySQL 的实际应用场。

下一章：[34-Redis.md](./34-Redis.md)
