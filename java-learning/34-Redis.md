# 第三十四章 Redis 系统学习（从命令全解到高可用实战）

> 本章目标：系统掌握 Redis——从安装部署、五大数据类型与高级类型的命令全解，到持久化、内存淘汰、主从/哨兵/集群高可用，再到分布式锁、缓存治理与 Spring Boot 实战。与第十二章（应用篇）、第二十九章（原理深度篇）配套：12 章"会用"、29 章"懂原理"、本章"全链路掌握"。
>
> 前置知识：第十二章 Redis 缓存（基础用法）。

## 34.1 Redis 概述与安装

### 34.1.1 Redis 是什么

Redis（Remote Dictionary Server）= 基于内存的 **键值（Key-Value）数据库**。

| 特性 | 说明 |
| --- | --- |
| 内存存储 | 读写微秒级，10 万+ QPS |
| 数据结构丰富 | String/Hash/List/Set/ZSet + Bitmap/Geo/HyperLogLog/Stream |
| 单线程 + IO 多路复用 | 命令原子执行，无锁竞争（6.0 后网络 IO 多线程） |
| 持久化 | RDB 快照 + AOF 日志 |
| 高可用 | 主从复制、哨兵、Cluster 集群 |
| 生态完善 | 分布式锁、缓存、消息队列、排行榜等场景全覆盖 |

### 34.1.2 安装启动

```bash
# 方式一：Docker（推荐，第 19 章已学）
docker run -d --name redis -p 6379:6379 redis:7

# 方式二：Linux 源码编译
wget https://download.redis.io/releases/redis-7.2.4.tar.gz
tar -xzf redis-7.2.4.tar.gz && cd redis-7.2.4
make && make install
redis-server redis.conf      # 指定配置启动
redis-cli -p 6379            # 连接
redis-cli -a password        # 带密码连接（8.0 后可用 --user）
```

> Windows 官方不支持，生产用 Linux/Docker，开发可用 WSL 或 Memurai（Windows 版替代）。

### 34.1.3 常用配置（redis.conf）

```conf
bind 0.0.0.0                # 监听所有网卡（生产按需收窄）
port 6379
requirepass yourpassword    # 密码（生产必开）
daemonize yes               # 后台运行
maxmemory 2gb               # 最大内存（配合淘汰策略）
appendonly yes              # 开启 AOF
```

## 34.2 五大数据类型（命令全解）

> 速记口诀：**String 字符串、Hash 哈希、List 列表、Set 集合、ZSet 有序集合**。

### 34.2.1 String（最常用）

```bash
# 基本
SET user:1:name zhangsan          # 设置
GET user:1:name                   # 获取
MSET k1 v1 k2 v2                  # 批量设置
MGET k1 k2                        # 批量获取
DEL user:1:name                   # 删除
EXISTS user:1:name                # 是否存在
SETEX code 60 123456              # 设置+60 秒过期（验证码场景）
SETNX lock:order 1                # 不存在才设置（分布式锁基础）

# 数值操作（原子自增，计数器/点赞/库存）
INCR counter                      # +1
DECR counter                      # -1
INCRBY counter 10                 # +10
INCRBYFLOAT price 0.5             # 浮点 +0.5

# 字符串截取
APPEND key "more"                 # 追加
STRLEN key                        # 长度
GETRANGE key 0 3                  # 截取 [0,3]
```

| 场景 | 命令组合 |
| --- | --- |
| 缓存对象 | `SET user:1 {"id":1,"name":"zhangsan"} EX 600` |
| 计数器 | `INCR 浏览量 / INCRBY 库存` |
| 验证码 | `SETEX code 60 123456` |
| 分布式锁 | `SET lock nx ex 30` |

### 34.2.2 Hash（对象存储）

```bash
HSET user:1 name zhangsan age 20 phone 13800001111   # 存对象（多个字段）
HGET user:1 name                  # 取单个字段
HMGET user:1 name age             # 取多个字段
HGETALL user:1                    # 取全部（慎用于大 hash）
HINCRBY user:1 age 1              # 字段原子自增（购物车数量）
HDEL user:1 phone                 # 删字段
HLEN user:1                       # 字段数量
HEXISTS user:1 name               # 字段是否存在
```

| 场景 | 说明 |
| --- | --- |
| 用户/商品信息缓存 | 按字段更新不整体覆盖，省流量 |
| 购物车 | `HSET cart:1 sku_1001 2`（sku -> 数量） |
| 会话 Session | hash 存用户信息 |

> **String vs Hash 存对象**：String 整体序列化（JSON），一次全取全存；Hash 按字段存取，修改单字段更高效。字段多且频繁改单字段用 Hash。

### 34.2.3 List（列表：消息队列/时间线）

```bash
LPUSH logs err1 err2          # 左插入（头部）
RPUSH logs info1              # 右插入（尾部）
LRANGE logs 0 -1              # 取全部
LPOP logs                     # 左弹出（消费）
RPOP logs                     # 右弹出
LLEN logs                     # 长度
LINDEX logs 0                 # 按下标取
LTRIM logs 0 99               # 截断（只留前 100 条，日志/消息防膨胀）
BLPOP queue 5                 # 阻塞弹出（无数据等最多 5 秒，消息队列核心）
```

| 场景 | 说明 |
| --- | --- |
| 简单消息队列 | `RPUSH 生产 + BLPOP 消费`（阻塞式，轮询开销低） |
| 时间线/动态流 | `LPUSH 最新动态`，`LRANGE 0 9` 取前 10 |
| 栈 | 同向 `LPUSH + LPOP` |
| 日志收尾 | `LPUSH + LTRIM` 只保留最近 N 条 |

### 34.2.4 Set（集合：去重/共同好友）

```bash
SADD online user1 user2 user3    # 添加
SMEMBERS online                  # 取全部
SREM online user1                # 移除
SCARD online                     # 元素数量
SISMEMBER online user1           # 是否包含（O(1)）
SRANDMEMBER online 2             # 随机取 2 个（抽奖）
SPOP online                      # 随机弹出（抽奖后删除）
SINTER set1 set2                 # 交集（共同好友）
SUNION set1 set2                 # 并集
SDIFF set1 set2                  # 差集（set1 有 set2 没有）
```

| 场景 | 说明 |
| --- | --- |
| 去重 | 点赞/已读用户集合 |
| 共同好友/可能认识 | `SINTER` 交集 |
| 抽奖 | `SRANDMEMBER`（可重复）/ `SPOP`（抽后移除） |
| 在线用户统计 | 上线 `SADD`，下线 `SREM`，`SCARD` 计数 |
| 关注模型 | 关注/粉丝各一个 Set |

### 34.2.5 ZSet（有序集合：排行榜核心）

```bash
ZADD rank 98 java 95 go 88 python     # 添加：member 带分数 score
ZRANGE rank 0 -1                      # 按分数升序取全部
ZREVRANGE rank 0 2                     # 降序取前 3（排行榜 Top3）
ZRANGEBYSCORE rank 90 100             # 按分数区间取
ZSCORE rank java                       # 查分数
ZINCRBY rank 5 java                    # 分数 +5（点赞/热度更新）
ZRANK rank java                        # 查排名（升序下标）
ZREVRANK rank java                     # 查排名（降序）
ZCARD rank                             # 元素数量
ZREM rank java                         # 删除元素
```

| 场景 | 说明 |
| --- | --- |
| 排行榜 | `ZINCRBY 热度 +1` → `ZREVRANGE 0 9` 取 Top10 |
| 延时队列 | score = 执行时间戳，轮询 `ZRANGEBYSCORE 0 now` |
| 用户积分/等级 | 分数即积分 |
| 范围查询 | 按时间/价格排序的数据 |

### 34.2.6 五种类型对比速查

| 类型 | 底层结构 | 特性 | 典型场景 |
| --- | --- | --- | --- |
| String | SDS | 最简单，原子自增 | 缓存、计数器、锁 |
| Hash | 哈希表 + ziplist | 字段级操作 | 对象、购物车 |
| List | quicklist | 双端队列 | 消息、时间线 |
| Set | intset + 哈希表 | 去重、集合运算 | 抽奖、共同好友 |
| ZSet | skiplist + 哈希表 | 有序 + 分数 | 排行榜、延时队列 |

## 34.3 高级数据类型

### 34.3.1 Bitmap（位图：签到/在线统计）

底层是 String 的位操作，1 个 key 存 8 位/字节，亿级用户状态只占几十 MB。

```bash
SETBIT sign:20250601 100 1        # 用户 100 第 6 月 1 日签到
GETBIT sign:20250601 100          # 查是否签到
BITCOUNT sign:20250601            # 当日签到总人数
BITOP AND result sign:1 sign:2    # 位运算（连续签到用户）
```

### 34.3.2 HyperLogLog（基数统计：UV）

去重计数，12KB 存 2^64 个元素，误差 0.81%（基数统计，不需要精确值时首选）。

```bash
PFADD uv:20250601 u1 u2 u3 u1     # 添加（自动去重）
PFCOUNT uv:20250601               # 统计独立用户数 = 3
PFMERGE uv:total uv:1 uv:2        # 合并多天
```

### 34.3.3 Geo（地理位置：附近的人）

```bash
GEOADD city 116.40 39.90 beijing 121.47 31.23 shanghai
GEODIST city beijing shanghai km          # 两点距离
GEOPOS city beijing                        # 坐标
GEORADIUS city 116.40 39.90 100 km        # 附近 100km 的点
```

### 34.3.4 Stream（消息队列：Redis 5.0+）

比 List 更完善的消息队列：支持消费者组、ACK、持久化。

```bash
XADD order-stream * user_id 1 amount 99.9   # 生产消息（* 自动生成 ID）
XLEN order-stream                            # 消息数量
XREAD COUNT 1 STREAMS order-stream 0         # 从头读 1 条
XGROUP CREATE order-stream group1 0          # 创建消费者组
XREADGROUP GROUP group1 consumer1 COUNT 1 STREAMS order-stream >   # 消费
XACK order-stream group1 <消息ID>            # 确认消费（不确认会重复投递）
```

> 生产选型：轻量场景用 List 阻塞队列即可；需要消费组、可靠投递、回溯用 Stream；再重就上 RabbitMQ/Kafka（第 16/27 章）。

## 34.4 通用命令与 Key 规范

### 34.4.1 通用命令

```bash
KEYS user:*                  # 全库匹配（生产禁用！会阻塞，用 SCAN）
SCAN 0 MATCH user:* COUNT 100   # 游标遍历，不阻塞
EXISTS key / TYPE key / TTL key / PTTL key
EXPIRE key 60                # 设置过期（秒）
PERSIST key                  # 取消过期
RENAME key newkey
SELECT 1                     # 切换库（0-15，不推荐多库混用）
FLUSHDB / FLUSHALL           # 清库（生产慎用！）
```

### 34.4.2 Key 命名规范

```
业务:对象:ID[:字段]     # 用冒号分层
user:1                  # 用户缓存
user:1:cart             # 用户购物车
order:20250601:list     # 某天订单列表
product:1001:stock      # 商品库存
```

### 34.4.3 大 Key 与热 Key 治理

| 问题 | 识别 | 处理 |
| --- | --- | --- |
| **大 Key** | `MEMORY USAGE key`、`redis-cli --bigkeys`（>1MB/元素>1万） | 拆分（hash 分片）、压缩、`LTRIM`/`UNLINK` 渐进删除 |
| **热 Key** | 单 key QPS 极高 | 多级缓存（本地缓存+Redis）、副本扩散（key1/key2）、限流 |

```bash
redis-cli --bigkeys           # 扫描大 key
MEMORY USAGE user:1           # 单 key 内存
OBJECT ENCODING user:1        # 内部编码（判断是否升级）
SLOWLOG GET 10                # 慢查询日志
```

## 34.5 事务、管道与 Lua

### 34.5.1 事务（MULTI/EXEC）

Redis 事务 = 命令**按顺序批量执行**（没有回滚，命令错误不影响其他命令）。

```bash
MULTI                        # 开启
INCR counter
INCRBY counter 10
EXEC                         # 一起执行
# 可用 DISCARD 取消；WATCH 实现乐观锁（类似 MySQL 版本号）
```

```bash
WATCH stock                 # 监视 key
MULTI
DECR stock                  # 执行前若 stock 被改，EXEC 返回 nil 并放弃执行
EXEC
```

### 34.5.2 管道（Pipeline）

一次网络往返发送多条命令，减少 RTT（网络往返），批量操作性能提升数倍到数十倍。

```bash
# 命令行：每行一条命令，一次提交
printf 'SET k1 v1\nSET k2 v2\nINCR c\n' | redis-cli --pipe
```

```java
// Jedis 管道示例
Pipeline pipe = jedis.pipelined();
for (int i = 0; i < 10000; i++) pipe.set("key:" + i, "v" + i);
pipe.sync();
```

### 34.5.3 Lua 脚本（原子操作核心）

Lua 脚本整体原子执行，解决"多条命令需要原子性"的问题（如扣库存 + 校验）。

```bash
# 脚本：库存不足返回 0，否则扣减返回 1（CAS 原子版）
EVAL "if tonumber(redis.call('GET', KEYS[1])) < tonumber(ARGV[1]) then return 0; else return redis.call('DECRBY', KEYS[1], ARGV[1]); end" 1 stock 5

# 用脚本实现分布式锁（SET NX EX 的原子替代）
EVAL "if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then return 1; else return 0; end" 1 lock:order user1 30
```

> **为什么 Lua 重要**：Redis 单线程执行 Lua 脚本，多条命令打包为一条，天然原子，是分布式锁、限流（令牌桶）等场景的标准实现。

## 34.6 持久化（RDB / AOF）

### 34.6.1 RDB（快照）

| 项 | 说明 |
| --- | --- |
| 原理 | 定期把内存全量快照写入 dump.rdb |
| 触发 | `save 900 1`（900 秒内 1 次修改）；`bgsave` 手动 |
| 优点 | 文件小、恢复快、性能影响小（fork 子进程写） |
| 缺点 | **可能丢最近一次快照后的数据**；快照间隔数据量大 |

### 34.6.2 AOF（追加日志）

| 项 | 说明 |
| --- | --- |
| 原理 | 每条写命令追加到 appendonly.aof（类似 MySQL binlog） |
| fsync 策略 | `always`（最安全最慢）/ `everysec`（默认，丢 1 秒）/ `no`（交给 OS） |
| AOF 重写 | 文件膨胀后 `bgrewriteaof` 压缩为最小命令集 |
| 优点 | 最多丢 1 秒数据（everysec），安全 |
| 缺点 | 文件大、恢复比 RDB 慢 |

### 34.6.3 混合持久化（4.0+，推荐）

```
RDB 快照 + 增量 AOF：重启时先加载 RDB（快），再用 AOF 增量补（不丢）
appendonly yes
aof-use-rdb-preamble yes
```

> **生产建议**：开启 AOF `everysec` + 混合持久化；主从架构下从库可关闭 AOF（只靠主库同步）；定期 `redis-check-aof` 校验。

## 34.7 过期删除与内存淘汰

### 34.7.1 过期删除策略

| 策略 | 说明 |
| --- | --- |
| **惰性删除** | 访问 key 时才发现过期才删（省 CPU，但过期 key 占内存） |
| **定期删除** | 每隔 100ms 随机抽一批过期 key 删除（折中） |
| 总结 | 两者结合：惰性为主、定期兜底 |

### 34.7.2 内存淘汰策略（maxmemory 触发）

```conf
maxmemory 2gb
maxmemory-policy allkeys-lru    # 推荐配置
```

| 策略 | 说明 |
| --- | --- |
| noeviction | 不淘汰，写报错（默认） |
| allkeys-lru | **所有 key 中淘汰最近最少使用（推荐）** |
| allkeys-lfu | 所有 key 中淘汰最不频繁使用（热度倾斜场景） |
| volatile-lru | 只淘汰"设置了过期时间"的 key（保留永久 key） |
| volatile-ttl | 淘汰剩余时间最短的 key |

## 34.8 高可用架构

### 34.8.1 主从复制

```
写入 → Master（主）
          │  bgsave 生成 RDB 全量同步 + 增量命令传播（类似 binlog）
          ▼
      Slave（从）← 读请求直接打到从库，分担读压力
```

```bash
# 从库配置（redis.conf）
replicaof 192.168.1.10 6379     # 指向主库
replica-read-only yes

# 或命令动态指定
redis-cli REPLICAOF 192.168.1.10 6379
redis-cli INFO replication       # 查看主从状态
```

### 34.8.2 哨兵（Sentinel：主从自动故障转移）

主库宕机后，哨兵自动把从库提升为主库，实现**自动高可用**。

```
        ┌─── Sentinel（哨兵集群，奇数个 ≥3）
        │      监控 + 投票
  主库 <──> 从库1 ── 从库2        ← 主库挂 → 哨兵选新主
```

```bash
# 三个哨兵进程：redis-sentinel sentinel1.conf ... 
# sentinel.conf 关键配置
sentinel monitor mymaster 192.168.1.10 6379 2   # 2 = 至少 2 个哨兵同意才切换
sentinel down-after-milliseconds mymaster 5000   # 主观下线判断
sentinel failover-timeout mymaster 60000         # 切换超时
```

Java 客户端（Spring Boot）只需连哨兵地址，自动发现当前主库：

```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes: 192.168.1.10:26379,192.168.1.11:26379,192.168.1.12:26379
```

### 34.8.3 Cluster 集群（数据分片，16384 槽位）

数据超过单机内存/单点写入瓶颈时，Cluster 把 key 哈希到 **16384 个槽**，分布在多节点：

```
hash(key) % 16384 → 槽 → 对应节点
key 分片：节点 A 槽 0-5460 / 节点 B 5461-10922 / 节点 C 10923-16383
```

```bash
# 6 节点（3 主 3 从）快速搭建
redis-cli --cluster create \
  192.168.1.10:7000 192.168.1.11:7001 192.168.1.12:7002 \
  192.168.1.10:7003 192.168.1.11:7004 192.168.1.12:7005 \
  --cluster-replicas 1
redis-cli --cluster check 192.168.1.10:7000    # 检查槽位分配
```

| 特性 | 说明 |
| --- | --- |
| 无中心化 | 每节点都认识整个拓扑，客户端可连任意节点 |
| 自动分片 | 16384 槽位按 key 路由 |
| 高可用 | 主挂从自动顶上（内部有类哨兵机制） |
| 限制 | 不支持多 key 跨节点操作（`MGET k1 k2` 在不同槽报错）；可用 hash tag `{user:1}:a` 强制同槽 |
| 客户端 | 连接任意节点，收到 MOVED/ASK 重定向自动处理 |

### 34.8.4 三种架构选型

| 架构 | 解决 | 适用 |
| --- | --- | --- |
| 单机 | 起步 | 开发/小流量 |
| 主从 + 哨兵 | 读压力 + 主挂自动切换 | **大多数生产场景（首选）** |
| Cluster | 数据量/写入超单机 | 大促、海量数据 |

## 34.9 分布式锁

### 34.9.1 场景与要求

多实例同时扣库存/重复下单（第 31 章防超卖），单机锁失效。分布式锁要求：**互斥、防死锁、可重入、防误删**。

### 34.9.2 SETNX 手写版

```bash
# 加锁：不存在才设置，30 秒自动过期防死锁
SET lock:order 1001 NX EX 30
# 业务... 
# 解锁：先判断是自己的值再删（Lua 原子，防误删别人的锁）
EVAL "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end" 1 lock:order 1001
```

```java
// Jedis 实现
String token = UUID.randomUUID().toString();
// 加锁（SETNX + 过期）
Boolean ok = jedis.set("lock:order", token, SetParams.setParams().nx().ex(30));
if (ok) {
    try { /* 业务逻辑 */ }
    finally { unlock(jedis, "lock:order", token); }   // Lua 比对后 DEL
}
```

### 34.9.3 Redisson（生产推荐）

```java
// 1. 依赖 org.redisson:redisson-spring-boot-starter
RLock lock = redisson.getLock("lock:order");
try {
    // 尝试 3 秒获取，超时返回 false
    if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
        // 业务（可重入：同一线程可重复获取）
    }
} finally {
    lock.unlock();
}
```

Redisson 解决了手写版的三大坑：

| 问题 | Redisson 方案 |
| --- | --- |
| 业务超时锁自动释放导致并发 | **看门狗**：锁未释放时自动续期（默认 30s，每 10s 续） |
| 不可重入 | 内部用 hash 计数，同线程可重入 |
| 主从切换锁丢失 | 可选 **红锁 RedLock**（多节点过半加锁） |

> **注意**：Redis 锁适合"非强一致"场景（幂等校验、防重）。金融级强一致请用 ZooKeeper（第 23 章）或数据库行锁。

## 34.10 缓存治理（三大问题 + 一致性）

### 34.10.1 缓存穿透（查不存在的数据）

**现象**：恶意请求疯狂查不存在的 key，全部打到数据库。

| 方案 | 说明 |
| --- | --- |
| **布隆过滤器** | 请求先过过滤器，不存在的直接拒绝（Spring 可用 Guava BloomFilter / Redisson RBloomFilter） |
| **缓存空值** | 查不到也缓存空对象（TTL 短，如 60s），防打穿 |
| 参数校验 | 非法 ID（负数、超长）直接拦截 |

```java
// 布隆过滤器防穿透
RBloomFilter<Long> filter = redisson.getBloomFilter("productBloom", 1000000, 0.01);
filter.tryInit(1000000, 0.01);      // 预估容量 + 误判率
if (!filter.contains(productId)) {
    return Result.fail("商品不存在");    // 一定不存在，直接返回
}
```

### 34.10.2 缓存击穿（热点 key 过期瞬间）

**现象**：一个热点 key 过期瞬间，大量请求同时打数据库。

| 方案 | 说明 |
| --- | --- |
| **互斥锁** | 只有一个线程去查库重建缓存，其他等待/快速失败 |
| **逻辑过期** | 缓存不设物理过期，value 带逻辑过期时间；过期时异步重建 |
| 热点不过期 | 后台定时刷新 |

```java
// 互斥锁重建缓存（简洁版）
String cache = redis.get(key);
if (cache == null) {
    if (redis.setnx("lock:" + key, "1", 5)) {     // 抢到锁的才去查库
        try {
            cache = db.query(key);
            redis.set(key, cache, 600);
        } finally {
            redis.del("lock:" + key);
        }
    } else {
        Thread.sleep(50);                          // 没抢到，稍后重查缓存
    }
}
```

### 34.10.3 缓存雪崩（大量 key 同时过期）

**现象**：大量 key 同一时间过期（如缓存预热后同时失效），请求全部打库。

| 方案 | 说明 |
| --- | --- |
| **过期时间加随机值** | `EXPIRE key 600 + random(0~300)`，错开过期 |
| **多级缓存** | 本地缓存（Caffeine）+ Redis 双级 |
| **限流降级** | 数据库被压时熔断降级（呼应第 15/30 章 Sentinel） |
| **集群高可用** | 参考 34.8 主从/哨兵，Redis 本身不挂 |

### 34.10.4 缓存一致性（先更库还是先删缓存）

| 方案 | 流程 | 问题 |
| --- | --- | --- |
| 先删缓存再更库 | 删缓存 → 更库 | 更新间隙有人读旧库回填脏缓存 |
| **先更库再删缓存（推荐）** | 更库 → 删缓存 | 删除失败则脏数据（配重试/Canal） |
| **延迟双删** | 更库 → 删缓存 → 延迟 500ms 再删 | 容忍短暂不一致，简单有效 |

```java
// 延迟双删（简单版）
updateDb();                      // 1. 更新数据库
redis.del(key);                  // 2. 删除缓存
Thread.sleep(500);               // 3. 等可能存在的回填窗口过去
redis.del(key);                  // 4. 再次删除兜底
```

> **终极方案**：Canal 监听 MySQL binlog → 异步删缓存（第 31 章商城实战采用类似思路）。**一致性要求极高**（余额）别用缓存，直接读库。

## 34.11 Spring Boot 集成

### 34.11.1 依赖与配置

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: 
      database: 0
      timeout: 3000ms
      lettuce:
        pool:                    # 连接池（需 commons-pool2 依赖）
          max-active: 16
          max-idle: 8
```

### 34.11.2 RedisTemplate 使用与序列化

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        // key 用 String 序列化（可读），value 用 JSON（Jackson2JsonRedisSerializer）
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> valueSerializer =
                new Jackson2JsonRedisSerializer<>(ObjectMapper.class, Object.class);
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        return template;
    }
}
```

```java
@Service
public class UserCacheService {

    @Autowired
    private StringRedisTemplate redis;   // 存 String，最常用

    // 缓存用户信息（JSON）
    public User getUserById(Long id) {
        String json = redis.opsForValue().get("user:" + id);
        if (json != null) return JSON.parseObject(json, User.class);
        User user = userMapper.selectById(id);
        redis.opsForValue().set("user:" + id, JSON.toJSONString(user), 30, TimeUnit.MINUTES);
        return user;
    }

    // Hash 购物车
    public void addCart(Long userId, Long skuId, int count) {
        redis.opsForHash().increment("cart:" + userId, skuId.toString(), count);
    }
}
```

### 34.11.3 Spring Cache 注解（零侵入缓存）

```java
@Cacheable(cacheNames = "product", key = "#id")          // 先查缓存，没有则执行方法并缓存
public Product getProduct(Long id) { return productMapper.selectById(id); }

@CacheEvict(cacheNames = "product", key = "#product.id") // 更新后清缓存
public void updateProduct(Product product) { productMapper.updateById(product); }

@CachePut(cacheNames = "product", key = "#product.id")   // 每次执行并更新缓存
public Product saveProduct(Product product) { ... }
```

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 30m          # 统一过期时间
      cache-null-values: false   # 不缓存 null
```

> **注意**：`@Cacheable` 默认把方法返回值序列化进 Redis；key 需 `SPEL` 指定（`#id`）；更新场景必须配 `@CacheEvict`，否则读到旧数据。mall-boot 项目即采用此模式。

## 34.12 实战案例（代码级）

### 案例 1：验证码（SETEX + 校验）

```java
public void sendCode(String phone) {
    String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));  // 6 位
    redis.opsForValue().set("sms:code:" + phone, code, 5, TimeUnit.MINUTES); // 5 分钟有效
    smsClient.send(phone, code);
}

public boolean verifyCode(String phone, String code) {
    String cached = redis.opsForValue().get("sms:code:" + phone);
    if (code != null && code.equals(cached)) {
        redis.delete("sms:code:" + phone);      // 一次性
        return true;
    }
    return false;
}
```

### 案例 2：排行榜（ZSet）

```java
// 点赞/热度 +1
public void like(Long articleId) {
    redis.opsForZSet().incrementScore("rank:article", articleId.toString(), 1);
}
// Top10
public List<Long> top10() {
    Set<ZSetOperations.TypedTuple<String>> top =
        redis.opsForZSet().reverseRangeWithScores("rank:article", 0, 9);
    return top.stream().map(t -> Long.valueOf(t.getValue())).toList();
}
```

### 案例 3：UV 统计（HyperLogLog）

```java
public void visit(String userId) { redis.opsForHyperLogLog().add("uv:20250601", userId); }
public long uv() { return redis.opsForHyperLogLog().size("uv:20250601"); }
```

### 案例 4：商品详情缓存 + 防击穿（综合）

```java
public Product getProductSafe(Long id) {
    // 1. 布隆过滤器先挡一层（防穿透）
    if (!bloom.contains(id)) return null;
    // 2. 读缓存
    String json = redis.opsForValue().get("product:" + id);
    if (json != null) return JSON.parseObject(json, Product.class);
    // 3. 缓存未命中 → 互斥锁重建（防击穿）
    if (redis.opsForValue().setIfAbsent("lock:product:" + id, "1", 5, TimeUnit.SECONDS)) {
        try {
            Product p = productMapper.selectById(id);
            redis.opsForValue().set("product:" + id, JSON.toJSONString(p),
                300 + ThreadLocalRandom.current().nextInt(300), TimeUnit.SECONDS);  // 防雪崩：随机 TTL
            return p;
        } finally {
            redis.delete("lock:product:" + id);
        }
    } else {
        Thread.sleep(50);   // 等锁，重查缓存
        return getProductSafe(id);
    }
}
```

## 34.13 练习与总结

### 练习题（命令行实操）

```bash
# 1. 用五种类型分别实现：缓存用户、队列、关注列表、共同好友、点赞排行榜
# 2. 用 SETEX 实现 60 秒验证码；用 INCR 实现访问计数
# 3. 用 ZSet 实现"近 7 天热榜"：ZINCRBY + ZREVRANGE
# 4. 启动主从 + 哨兵，kill 主库观察自动切换（redis-cli INFO replication）
# 5. 用 Lua 实现"库存扣减 + 防止超卖"的原子脚本
# 6. 在 Spring Boot 里用 @Cacheable 缓存商品列表，压测对比命中/未命中耗时
```

### 面试高频问题清单

| 问题 | 答案要点 |
| --- | --- |
| Redis 为什么快？ | 内存 + 单线程（无锁）+ IO 多路复用 + 高效数据结构 |
| 五种类型底层结构？ | SDS/quicklist/hashtable+ziplist/intset/skiplist（详见 29 章） |
| 缓存穿透/击穿/雪崩？ | 布隆/空值；互斥锁/逻辑过期；随机 TTL/多级缓存 |
| 缓存一致性方案？ | 先更库再删缓存 + 延迟双删 / Canal binlog |
| RDB 和 AOF 区别？ | 快照 vs 日志；丢数据量 vs 文件大小；恢复速度 |
| 主从同步原理？ | RDB 全量 + 增量命令传播（repl_backlog） |
| 哨兵怎么工作？ | 监控 + 主观/客观下线 + 投票选新主 + 通知 |
| 分布式锁实现？ | SETNX+EX、Redisson 看门狗、RedLock |
| 大 key 危害？ | 阻塞主线程、网络传输慢、内存不均；用 --bigkeys 排查 |

### 本章小结

- **命令层**（34.2~34.5）：五大数据类型 + 高级类型 + 事务/管道/Lua；
- **可靠性**（34.6~34.7）：RDB/AOF/混合持久化 + 过期与淘汰策略；
- **高可用**（34.8）：主从 → 哨兵 → Cluster 演进路线；
- **工程实践**（34.9~34.12）：分布式锁、缓存三防、一致性、Spring Boot 集成、四个实战案例。

配套：第 12 章（应用入门）、第 29 章（原理深度）、第 31 章（商城缓存实战）、第 32 章（微服务缓存治理）。

下一章：[35-Docker.md](./35-Docker.md)
