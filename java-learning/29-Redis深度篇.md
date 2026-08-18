# 第二十九章 Redis 深度篇（原理与高可用）

> 第十二章学会了 Redis 的"怎么用"，本章深入"**为什么这样设计**"：底层数据结构、持久化、内存淘汰、集群高可用、缓存一致性、分布式锁深挖。**面试问 Redis，一半的问题出自本章。**

## 29.1 为什么需要 Redis 深度篇

第十二章我们掌握了五种数据类型和 Spring Boot 整合，但线上系统出现问题时你会发现：

- 缓存和数据库数据不一致，到底先删缓存还是先更新数据库？
- 大促当天缓存雪崩，接口全部超时，怎么办？
- 分布式锁突然失效，重复下单了？
- 主节点宕机，Redis 为什么不能自动切换？

这些问题的答案不在 API 层面，而在**原理层面**。本章把 Redis 从"会用"推向"懂它"。

### 29.1.1 Redis 为什么快

| 因素 | 说明 |
| --- | --- |
| 纯内存操作 | 读写内存，无磁盘 IO（持久化是异步的） |
| 单线程模型 | 无锁、无上下文切换、无竞争开销（6.0 后网络 IO 多线程，命令执行仍单线程） |
| IO 多路复用 | epoll 同时监听多个 socket，一个线程服务海量连接 |
| 高效数据结构 | SDS、跳表、压缩列表等针对场景专门设计 |
| 对象共享池 | 整数对象共享，减少内存分配 |

> **单线程为什么还快？** 因为瓶颈在内存和网络，不在 CPU。单线程反而避免了锁竞争和线程切换。面试常问："Redis 6.0 引入多线程，为什么命令还是串行执行？"——多线程只处理**网络读写**，命令执行保持单线程保证**原子性**。

### 29.1.2 常用操作回顾（快速热身）

```bash
# 5 种基础类型（第十二章已详述，这里只列命令）
SET user:1 "zhangsan" EX 60        # 字符串 + 过期
LPUSH logs "err1"                   # 列表
SADD online "u1" "u2"               # 集合
ZADD score 98 "java"                # 有序集合
HSET user:1 name "zhangsan" age 20  # 哈希

# 本章新增重点命令
SCAN 0 MATCH user:* COUNT 100       # 游标遍历，替代 KEYS（防阻塞）
INFO memory                          # 内存信息
SLOWLOG GET 10                       # 慢查询日志
OBJECT ENCODING user:1               # 查看 key 内部编码（排查压缩列表是否升级）
MEMORY USAGE user:1                  # 查看 key 占用内存
```

## 29.2 底层数据结构（面试必问）

五种数据类型是"对外接口"，底层由多种数据结构实现，且**可动态切换**（升级）。

### 29.2.1 八大底层结构

| 底层结构 | 说明 | 对应使用场景 |
| --- | --- | --- |
| SDS（简单动态字符串） | 记录长度、预分配、二进制安全，C 字符串升级版 | String、键名 |
| 双向链表 linkedlist | 头尾指针 + 节点计数 | List（元素多/长字符串时） |
| 压缩列表 ziplist | 连续内存、元素紧凑，省内存 | List/Hash/ZSet 元素少时 |
| 快速列表 quicklist | ziplist 链表化，兼顾内存与性能 | List（默认） |
| 哈希表 hashtable | 渐进式 rehash，读写 O(1) | Hash、Set、全局字典 |
| 整数集合 intset | 只存整数、有序、升级机制 | Set 全为整数且少时 |
| 跳表 skiplist | 多层索引链表，O(logN) 查找，实现简单 | ZSet、Cluster 槽分配 |
| 紧凑列表 listpack | ziplist 的改进版（7.0+） | 取代 ziplist |

### 29.2.2 SDS 为什么不用 C 字符串

```c
// C 字符串问题：
// 1. 以 '\0' 结尾 → 无法存储二进制数据（如图片）
// 2. 获取长度要 O(n) 遍历
// 3. 拼接要手动分配内存，可能溢出

// Redis 的 SDS（简化版）：
struct sdshdr {
    int len;        // 已用长度，O(1) 获取
    int alloc;      // 已分配容量，预留空间减少扩容
    char buf[];     // 字节数组，不依赖 '\0'，二进制安全
};
```

**面试回答**：SDS 相比 C 字符串有 3 个优势——① O(1) 获取长度；② 空间预分配 + 惰性释放，减少内存分配次数；③ 二进制安全，可以存任意数据。

### 29.2.3 跳表（ZSet 的核心）

```
head ──► [层3] ──────────────► 90 ──────► NULL
          │                     │
head ──► [层2] ──► 50 ──► 80 ──► 90 ──► 100
          │        │     │     │       │
head ──► [层1] ──► 50 ──► 80 ──► 90 ──► 100   ← 底层是完整有序链表
```

- 每一层是下一层的"高速公路"，查找从最高层开始，**一次跳过多个节点**，时间复杂度 O(logN)
- 相比平衡树（红黑树）：实现简单、区间查找方便、不用旋转调整
- **为什么不用红黑树？** 跳表实现更简单、调试容易、支持范围查询，对 Redis 这种"写多读多"的场景足够

### 29.2.4 哈希表的渐进式 rehash

当元素过多触发扩容时，如果一次性搬迁所有数据会**阻塞服务**。Redis 的做法：

```
rehashidx 从 0 开始，每次增删改查顺带搬一个桶
┌─────────────────────────────────────────────┐
│ ht[0]（旧表）──逐步搬移──► ht[1]（新表，2 倍大小） │
└─────────────────────────────────────────────┘
搬迁期间：新增只进新表，查询先查新表再查旧表
全部搬完后：交换指针，rehashidx 置 -1
```

**这就是为什么大 key（超大 Hash）不要随便删**：`DEL` 一个包含百万字段的 Hash 会阻塞主线程几秒，正确做法用 `HSCAN` 分批删除或 `UNLINK` 异步删除。

## 29.3 持久化：RDB 与 AOF

### 29.3.1 两种持久化对比

| 对比项 | RDB（快照） | AOF（追加日志） |
| --- | --- | --- |
| 内容 | 某一时刻全量数据的二进制快照 | 记录每次写命令 |
| 恢复速度 | 快（直接加载） | 慢（重放命令） |
| 数据丢失 | 可能丢最后一次快照后的数据 | 取决于 fsync 策略，最多丢 1 秒 |
| 文件大小 | 小 | 大（可重写压缩） |
| 对性能影响 | fork 子进程写盘，父进程不阻塞 | 每写一条命令有 IO 开销 |

### 29.3.2 RDB 的 fork 原理（面试考点）

```
主进程 fork 出子进程
主进程继续服务（copy-on-write：写时复制）
子进程把内存快照写入 dump.rdb
```

- `fork` 采用 **COW（写时复制）**：子进程共享主进程内存页，只有主进程**修改**的页才复制一份
- 所以 RDB 生成期间主进程**几乎不阻塞**，但大实例 fork 会瞬间占用额外内存和短暂卡顿
- **配置建议**：`save 900 1`（900 秒内 1 次修改）→ 生产环境常手动 `BGSAVE` 或关闭定时 save

### 29.3.3 AOF 的三种策略

| appendfsync | 说明 | 数据安全性 |
| --- | --- | --- |
| always | 每写一条命令就 fsync 磁盘 | 最安全，性能最差 |
| everysec | 每秒 fsync 一次 | **生产推荐**，最多丢 1 秒 |
| no | 交给操作系统决定 | 性能最好，可能丢更多 |

### 29.3.4 AOF 重写与混合持久化

- **AOF 重写**（`BGREWRITEAOF`）：把多条命令合并成最少命令（如 100 次 INCR 合并成一条 SET），文件瘦身
- **混合持久化**（4.0+，`aof-use-rdb-preamble yes`）：AOF 文件前半部分是 RDB 快照 + 后半部分是增量命令，**兼顾恢复速度和数据安全**，生产标配

## 29.4 过期删除与内存淘汰

### 29.4.1 过期删除策略（两种结合）

```
惰性删除：访问 key 时才检查是否过期，过期就删
   ✅ 省 CPU    ❌ 过期 key 占内存不释放

定期删除：每隔 100ms 随机抽一批 key 检查过期
   ✅ 平衡 CPU 与内存

结论：Redis 采用 惰性删除 + 定期删除 组合
```

### 29.4.2 内存淘汰策略（内存满了怎么办）

| 策略 | 含义 |
| --- | --- |
| noeviction（默认） | 不淘汰，写命令直接报错 |
| allkeys-lru | 从**所有 key** 中淘汰最近最少使用 |
| volatile-lru | 从**设置了过期时间**的 key 中淘汰 LRU |
| allkeys-random | 从所有 key 随机淘汰 |
| volatile-ttl | 优先淘汰剩余 TTL 最短的 key |
| allkeys-lfu / volatile-lfu | 按访问频率淘汰（LFU，4.0+） |

**生产建议**：`maxmemory-policy allkeys-lru`；热点数据用 LFU 更合适。

> **LRU vs LFU**：LRU 看"多久没用了"，LFU 看"用得少不少"。一个 key 昨天高频访问、今天偶尔访问，LRU 可能误杀，LFU 更公平。

### 29.4.3 内存淘汰模拟（面试手写思路）

```java
// 简单 LRU 实现思路：LinkedHashMap 的 accessOrder=true
import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    public LRUCache(int capacity) {
        // accessOrder=true：访问过的元素移到末尾
        super(capacity, 0.75f, true);
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;   // 超出容量淘汰最久未访问的
    }
}
```

## 29.5 缓存一致性（面试高频）

### 29.5.1 四种常见方案

| 方案 | 做法 | 问题 |
| --- | --- | --- |
| Cache Aside | 先更新 DB，再删缓存 | 标准方案，有极小概率不一致 |
| 先删缓存再更新 DB | 删缓存 → 写 DB | **并发下极易不一致，不推荐** |
| 延迟双删 | 更新 DB 后删缓存，再延迟 500ms 再删一次 | 缓解，仍有窗口 |
| 订阅 binlog 删除 | Canal 监听 MySQL binlog，异步删缓存 | 最可靠，复杂度最高 |

### 29.5.2 为什么"先更新 DB 再删缓存"也可能出问题

```
线程 A：更新 DB（旧值→新值）
线程 B：读缓存（缓存是旧值）→ 读 DB 拿到新值 → 回填缓存（新值）
线程 A：删除缓存
结果：缓存是新值，正确 ✅

问题场景（删缓存时缓存正好是旧值）：
线程 A：更新 DB → 删除缓存
线程 B：读缓存 miss → 读 DB 旧值（A 还没提交/或并发读）→ 回填旧值
线程 A：事务已提交
结果：缓存里是旧值 ❌   ← 极短窗口，概率低
```

**实战做法**：① 更新 DB → 删缓存；② 缓存 key 设置短 TTL 兜底（如 30~60s）；③ 核心数据用 binlog 订阅方案。

> **终极答案**：没有 100% 一致的方案，只能"降低不一致概率 + 缩短不一致窗口"。给缓存加 TTL 兜底是最简单有效的兜底。

## 29.6 分布式锁深挖（第十二章进阶）

第十二章用 `SETNX` 实现了基础锁，本章解决它的三大缺陷。

### 29.6.1 基础锁的三个坑

```java
// ❌ 坑 1：忘记设置过期时间，锁永远不释放（宕机死锁）
setnx lock "1"          // 加锁成功但没设过期时间

// ❌ 坑 2：设置了过期时间但业务超时，锁自动释放，别人拿到锁
// ❌ 坑 3：释放时可能删掉别人的锁（A 超时后 B 拿到锁，A 执行完删了 B 的锁）
```

### 29.6.2 正确姿势：Redisson 看门狗

```java
import org.redisson.Redisson;
import org.redisson.api.RLock;

// 初始化（第十二章已配置），加锁释放
RLock lock = redisson.getLock("order:1001");
try {
    // 尝试加锁，等待 3 秒，锁自动续期（看门狗默认 30 秒，业务没执行完自动续）
    if (lock.tryLock(3, TimeUnit.SECONDS)) {
        // 业务逻辑
    }
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();   // 只释放自己的锁（Lua 脚本校验 value）
    }
}
```

**看门狗（Watchdog）原理**：
1. 加锁时设置默认 30 秒过期
2. 后台线程每 10 秒检查一次，若锁还在且业务未完成，**自动续期 30 秒**
3. 业务完成释放锁，看门狗销毁

**为什么要 Lua 脚本**：加锁/解锁必须是原子操作。

```lua
-- 解锁脚本（Redisson 核心）：校验 value 是自己的才删
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
```

### 29.6.3 RedLock 的争议（面试加分项）

- **RedLock**：向 5 个独立 Redis 实例加锁，超过半数成功才算拿到锁——解决单点问题
- **争议**：分布式系统专家 Martin Kleppmann 指出 RedLock 依赖时钟假设，可能失效
- **结论**：绝大多数业务用 Redisson 单机锁 + 主从哨兵即可；追求极致可用才考虑 RedLock，且要接受其理论缺陷

### 29.6.4 锁粒度与续期实战建议

```
❌ 全表锁：lock("user:all")          // 所有用户下单互相阻塞
✅ 行级锁：lock("user:" + userId)    // 只锁当前用户
❌ 一把锁锁整个方法                  // 尽量缩小锁范围
✅ 只锁临界区（库存扣减那几行）
```

> **面试答"分布式锁怎么设计"**：加锁（SETNX + 过期时间）→ 锁续期（看门狗）→ 解锁（Lua 校验）→ 粒度（业务维度）→ 可重入（Redisson 支持）。Redis 锁有 5~8 秒超时/主从切换丢锁等风险，极端场景用 ZK（第 23 章）或数据库乐观锁。

## 29.7 高可用：主从、哨兵、Cluster

### 29.7.1 主从复制

```
主节点（Master）──复制──► 从节点（Slave）
  写操作                读操作（分担读压力）
```

- 复制流程：`psync` 全量同步（RDB + buffer）→ 增量同步（repl_backlog 环形缓冲）
- **主从延迟问题**：从库读到旧数据，强一致场景（如秒杀扣减）必须读主库
- 配置：`replicaof 主IP 6379` 或 `slaveof`（旧命令）

### 29.7.2 哨兵（Sentinel）故障转移

```
┌───────────────────────────────┐
│          Sentinel（哨兵集群）    │  互相通信，共同决策
│  ┌──────────┐  ┌──────────┐   │
│  │ Sentinel1 │  │ Sentinel2 │  │
│  └──────────┘  └──────────┘   │
└───────────────────────────────┘
        │ 监控
┌───────▼──────┐      ┌─────────────┐
│  Master      │      │  Slave      │
│  宕机！       │◄────►│  自动升级为主  │
└──────────────┘      └─────────────┘
```

- **主观下线**：单个哨兵认为主节点不可达
- **客观下线**：超过半数哨兵（quorum）认为不可达
- **选主规则**：从库优先级 → 复制偏移量最大 → runid 最小
- **脑裂风险**：原主节点网络隔离后恢复，可能同时存在两个主——客户端写入丢失，Redis 通过 `min-replicas-to-write` 参数缓解

### 29.7.3 Cluster 集群（数据分片）

```
槽位（slot）：0 ~ 16383，共 16384 个
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Node A    │  │ Node B    │  │ Node C    │
│ 0-5460    │  │ 5461-10922│  │ 10923-16383│
└──────────┘  └──────────┘  └──────────┘
key 归属：CRC16(key) % 16384 = 槽位
```

**核心概念**：
- **为什么是 16384 个槽**：心跳包用 2KB 位图表示槽状态，16384 个槽恰好紧凑；再大浪费内存
- **为什么不用一致性哈希**：槽方案让数据分布更均匀、迁移更可控（按槽迁移），一致性哈希需要虚拟节点且迁移粒度不可控
- **客户端重定向**：访问的 key 不在当前节点，返回 `MOVED 槽位 目标IP`，客户端跳转
- **集群至少 3 主 3 从**；每个主节点配从节点（副本），主挂从自动顶替

### 29.7.4 三种架构选型

| 架构 | 适用场景 | 优点 | 缺点 |
| --- | --- | --- | --- |
| 单机 | 缓存量 < 内存、无高可用要求 | 简单 | 单点故障 |
| 主从 + 哨兵 | 数据量不大但要求高可用 | 自动故障转移，读写分离 | 数据总量受单机内存限制 |
| Cluster | 数据量大（TB 级） | 水平扩展、分片存储 | 运维复杂、多 key 操作受限 |

> **面试高频**："你们 Redis 用什么架构？为什么？"——答：数据量几个 G，用主从 + 哨兵保证可用性；数据超单机内存或写入 QPS 超单机上限，用 Cluster 分片。**先想数据量，再定架构。**

## 29.8 高并发场景三大难题深挖

第十二章介绍过穿透/击穿/雪崩的基本解法，这里补上**落地细节与深坑**。

### 29.8.1 缓存穿透（查不存在的数据）

**问题**：恶意请求大量查询不存在的 key，每次都打到数据库。

| 方案 | 做法 | 局限 |
| --- | --- | --- |
| 缓存空值 | 查不到也缓存，TTL 设短（如 5 分钟） | 占用内存 |
| 布隆过滤器 | 请求先过布隆，不存在直接返回 | 有误判率、需预热 |
| 参数校验 | 非法 id（负数、超长）直接拦截 | 治标 |

**布隆过滤器原理**：一个位数组 + 多个哈希函数，判断"一定不存在"或"可能存在"。误判率可配置（`bloom_fpp`），10 万数据约占用 0.1MB 内存。

### 29.8.2 缓存击穿（热点 key 过期）

**问题**：某个**热点 key** 恰好过期，瞬间大量请求打到 DB（不是雪崩的全量，是单点）。

```
✅ 互斥锁重建：
请求来了 → 缓存 miss → 抢分布式锁
   ├─ 抢到锁的线程 → 查 DB → 回填缓存 → 释放锁
   └─ 没抢到锁的线程 → 自旋等一会 → 再查缓存（已回填）

✅ 逻辑过期（提前续命）：
缓存里存 [数据, 过期时间] 两个字段
查询时逻辑过期 → 返回旧数据 + 异步后台线程重建缓存
```

### 29.8.3 缓存雪崩（大量 key 同时过期 / Redis 宕机）

| 应对 | 做法 |
| --- | --- |
| 过期时间加随机值 | `EXPIRE key 3600 + random(0,300)`，避免同时过期 |
| 热点数据不过期 | 后台任务定时刷新 |
| 多级缓存 | 本地 Caffeine + Redis，Redis 挂还有本地兜底 |
| Redis 高可用 | 哨兵/集群，主从切换 |
| 限流降级 | 网关层限流，DB 扛不住时降级返回兜底数据 |

### 29.8.4 BigKey 与热 Key（运维头号杀手）

**BigKey 危害**：
- 删除/迁移阻塞主线程（`DEL` 大 key 卡顿秒级）
- 网络传输放大（10MB 的 value 每次读取都吃带宽）
- 内存不均（集群中某节点打满）

**如何发现**：`redis-cli --bigkeys` 扫描；`MEMORY USAGE key` 检查。
**如何治理**：
- 大 String → 拆分或压缩
- 大 Hash/List → 分片（`user:1:part1`、`user:1:part2`），或换 `HSCAN` 分批处理
- 删除用 `UNLINK`（异步删除，不阻塞）

**热 Key 治理**：本地缓存 + 多副本 key（`hot:1`、`hot:2` 分散到不同节点）+ 读写分离。

## 29.9 事务与 Lua 脚本

### 29.9.1 Redis 事务（MULTI/EXEC）

```bash
MULTI                 # 开启事务
SET stock:1001 10
DECR stock:1001
EXEC                  # 批量原子执行（不被打断）
```

**注意**：Redis 事务**没有回滚**——命令入队成功但执行出错，其他命令照常执行。**它保证的是"隔离"而不是"原子回滚"**。

### 29.9.2 Lua 脚本（真正的原子操作）

```lua
-- 扣库存 Lua：检查库存 > 0 再扣减，整个过程原子
local stock = tonumber(redis.call("GET", KEYS[1]))
if stock and stock > 0 then
    redis.call("DECR", KEYS[1])
    return 1
end
return 0
```

```java
// Spring Boot 执行 Lua
DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaText, Long.class);
Long result = redisTemplate.execute(script, Collections.singletonList("stock:1001"));
```

**为什么 Lua 安全**：Redis 执行 Lua 脚本期间**阻塞其他命令**，天然原子。秒杀扣库存、分布式锁、限流计数都是 Lua 的经典场景。

## 29.10 性能排查与运维命令

### 29.10.1 慢查询与排查四步法

```bash
SLOWLOG GET 10          # 查看最近 10 条慢查询
SLOWLOG LEN             # 慢查询数量
CONFIG GET slowlog-log-slower-than   # 阈值（默认 10000 微秒=10ms）
```

**排查四步法**：
1. `INFO commandstats` → 看哪个命令调用次数多、耗时高
2. `SLOWLOG` → 慢命令具体是什么
3. `MONITOR` → 实时监控命令流（**慎用，生产会拖垮性能，只能短时间**）
4. `redis-cli --latency` → 客户端到服务端网络延迟

### 29.10.2 内存排查

```bash
INFO memory            # used_memory / used_memory_human
MEMORY STATS           # 内存细分
redis-cli --bigkeys    # 扫描大 key
CONFIG GET maxmemory   # 最大内存限制
```

**内存碎片率**（`mem_fragmentation_ratio`）：>1.5 说明碎片多（重启或调 `activedefrag yes`）；<1 说明内存不足在交换。

### 29.10.3 连接排查

```bash
INFO clients           # 当前连接数
CONFIG GET maxclients  # 最大连接数
CLIENT LIST            # 查看所有客户端
CLIENT KILL IP:port    # 杀掉异常连接
```

> **连接池配置**（Spring Boot + Lettuce）：`max-total`、`max-idle`、`min-idle`、`max-wait`，别用默认值——高并发下连接不够会直接报"无法获取连接"。

## 29.11 实战：秒杀扣库存完整方案

整合本章所有知识点，实现一个健壮的秒杀扣库存：

```java
@Service
public class SeckillService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    /**
     * 1. Lua 原子扣库存（防超卖）
     * 2. 幂等：SETNX 用户维度，防止重复抢购
     * 3. 限流：滑动窗口/令牌桶控制入口 QPS
     */
    public boolean seckill(long seckillId, long userId) {
        // 幂等检查：一个用户只能抢一次（SETNX + TTL 兜底）
        Boolean first = redis.opsForValue()
                .setIfAbsent("seckill:" + seckillId + ":user:" + userId, "1",
                        Duration.ofMinutes(5));
        if (Boolean.FALSE.equals(first)) {
            return false;   // 重复抢购
        }

        // Lua 原子扣库存：库存 > 0 才扣
        Long result = redis.execute(script,
                Collections.singletonList("seckill:stock:" + seckillId));
        return result != null && result == 1L;
    }
}
```

**配合的治理手段**：

| 环节 | 手段 | 知识点 |
| --- | --- | --- |
| 入口 | 网关限流（Sentinel） | 第 30 章 |
| 幂等 | SETNX + TTL | 29.6 |
| 扣库存 | Lua 原子操作 | 29.9 |
| 库存预热 | 提前把库存加载进 Redis | 12 章 |
| 数据一致性 | 异步落库 + binlog 对账 | 29.5 |
| 高可用 | 主从 + 哨兵 / Cluster | 29.7 |
| 防穿透 | 布隆过滤器拦无效请求 | 29.8 |

## 29.12 小结与练习

**本章重点**：
- Redis 快的原因：内存 + 单线程 + IO 多路复用 + 高效数据结构
- 八大底层结构：SDS、跳表、ziplist、quicklist 等，会讲跳表 vs 红黑树
- 持久化：RDB（fork + COW）vs AOF（三种 fsync），生产用混合持久化
- 过期：惰性 + 定期；淘汰：`allkeys-lru` 生产标配
- 缓存一致性：更新 DB 后删缓存 + TTL 兜底，核心数据用 binlog 订阅
- 分布式锁：Redisson 看门狗 + Lua 解锁，锁粒度到业务维度
- 高可用：主从复制 → 哨兵故障转移 → Cluster 16384 槽
- 穿透/击穿/雪崩：空值缓存/布隆、互斥锁重建、过期随机化/多级缓存
- BigKey 用 UNLINK 删、`--bigkeys` 扫
- Lua 脚本 = 原子操作，秒杀扣库存标配

**面试题参考**：
1. Redis 为什么快？为什么单线程还这么快？
2. SDS 相比 C 字符串有哪些优势？
3. 跳表是什么？ZSet 为什么用跳表不用红黑树？
4. RDB 和 AOF 的区别？fork 是什么原理？
5. Redis 的过期删除策略和内存淘汰策略？
6. 缓存和数据库一致性怎么保证？
7. Redis 分布式锁怎么实现？有哪些坑？Redisson 看门狗原理？
8. Redis 为什么用 16384 个槽？为什么不用一致性哈希？
9. 缓存穿透、击穿、雪崩的区别和解决方案？
10. BigKey 有什么危害？怎么发现和治理？
11. Redis 事务能回滚吗？Lua 脚本为什么是原子的？
12. Redis 挂了怎么办？（哨兵/集群/多级缓存/限流降级）

**课后练习**：
1. 用 Docker 启动 Redis，把 29.10 的命令全部执行一遍。
2. 写一个 Lua 脚本实现"限流"（固定窗口计数器），用 Spring Boot 调用。
3. 自己搭一套主从 + 哨兵（3 个 Sentinel），把主节点 kill 掉，观察自动切换。
4. 搭一个 3 主 3 从 Cluster，往里面写 100 个 key，用 `CLUSTER KEYSLOT` 验证槽位分布。
5. 用 Redisson 实现一个带看门狗的可重入分布式锁，模拟业务超时观察续期。
6. 给你的项目缓存 key 加上随机过期时间，模拟并发访问验证雪崩缓解。

上一章：[28-设计原则深度.md](./28-设计原则深度.md) | 下一章：[30-微服务治理实战.md](./30-微服务治理实战.md) | 返回目录：[README.md](./README.md)
