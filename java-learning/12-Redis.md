# 第十二章 Redis 缓存

> 本章掌握：Redis 核心概念与五种数据类型、Java/Spring Boot 集成、缓存三大问题（穿透/击穿/雪崩）、验证码与分布式锁实战。
> Redis 是**互联网后端标配**，99% 的企业项目都在用，从数据库缓存到分布式锁、消息队列、排行榜，处处可见它的身影。面试必考。

## 12.1 Redis 是什么

### 12.1.1 为什么需要缓存

没有缓存时，每个请求都打数据库：

```
浏览器 → Controller → Service → 数据库 MySQL
                                    ▲ 每秒成千上万请求，数据库扛不住（磁盘 IO 慢）
```

加一层缓存后，热点数据放内存：

```
浏览器 → Controller → Service → 先查 Redis（内存，极快）
                                 ├─ 命中 → 直接返回
                                 └─ 未命中 → 查 MySQL → 写回 Redis
```

**效果**：读多写少的场景（商品详情、用户信息、验证码），数据库压力降低 90% 以上。

### 12.1.2 Redis 的特点

| 特点 | 说明 |
| --- | --- |
| **内存存储** | 读写微秒级（比 MySQL 快 10~100 倍），每秒可处理 10 万+ 请求 |
| **单线程 + IO 多路复用** | 天然无并发竞争，命令原子执行（这是实现分布式锁的基础） |
| **丰富的数据类型** | String / Hash / List / Set / ZSet 五大基础类型 |
| **持久化** | RDB 快照 + AOF 日志，宕机不丢数据 |
| **高可用** | 主从复制、哨兵、集群（Redis Cluster） |
| **功能扩展** | 过期时间、发布订阅、Lua 脚本、事务 |

> **一句话**：Redis = 跑在内存里的高性能键值数据库，是关系型数据库的最佳搭档。

### 12.1.3 安装启动

**Windows**：官方不支持 Windows，最省事的方式是用 **Docker** 或 WSL：

```bash
# 方式一：Docker 一键启动（推荐）
docker run -d --name redis -p 6379:6379 redis:7

# 方式二：Linux / macOS 源码安装
wget https://download.redis.io/releases/redis-7.2.4.tar.gz
tar -xzf redis-7.2.4.tar.gz && cd redis-7.2.4
make && make install
redis-server          # 启动服务（默认端口 6379）
redis-cli             # 另开终端连接
```

## 12.2 五种基础数据类型（重点）

### 12.2.1 总览

| 类型 | 底层结构 | 使用场景 | 典型命令 |
| --- | --- | --- | --- |
| **String** | SDS 动态字符串 | 缓存对象、计数器、验证码 | `set` `get` `incr` `setex` |
| **Hash** | 哈希表 | 对象属性存储（如用户信息） | `hset` `hget` `hgetall` |
| **List** | 双向链表 | 消息队列、最新列表、朋友圈时间线 | `lpush` `rpush` `lpop` `rpop` |
| **Set** | 哈希表 | 去重、共同好友、抽奖 | `sadd` `sismember` `sinter` |
| **ZSet** | 跳表 + 哈希表 | 排行榜、延迟队列、Top N | `zadd` `zrange` `zrevrange` |

### 12.2.2 命令行练习

```bash
# ① String：set / get / setex（带过期时间）/ incr（自增，原子操作）
redis-cli
> set user:1 张三
> get user:1
> setex code:13812345678 300 123456     # 验证码，300 秒过期
> ttl code:13812345678                  # 查看剩余时间：299
> incr pageview                          # 自增 1（点赞数、访问量）
> incrby pageview 10                     # 自增 10

# ② Hash：对象属性
> hset user:1001 name 张三 age 20 email zhang@qq.com
> hget user:1001 name                    # 张三
> hgetall user:1001                      # 所有字段
> hincrby user:1001 age 1                # 年龄自增 1

# ③ List：消息队列 / 时间线
> lpush news:20260818 "第1条新闻"         # 头插
> lpush news:20260818 "第2条新闻"
> lrange news:20260818 0 -1              # 取全部
> rpop news:20260818                     # 尾取（先进先出，模拟队列）

# ④ Set：去重 / 集合运算
> sadd tag:java spring                   # 给文章加标签
> sadd tag:java redis
> smembers tag:java                      # 全部成员
> sinter tag:java tag:redis              # 交集（既懂 java 又懂 redis 的文章）
> sunion tag:java tag:redis              # 并集

# ⑤ ZSet：排行榜（score 排序）
> zadd rank 100 "张三"
> zadd rank 95  "李四"
> zadd rank 120 "王五"
> zrevrange rank 0 -1 withscores         # 按分数从高到低：王五120、张三100、李四95
> zincrby rank 10 "李四"                  # 李四加 10 分 → 105
```

### 12.2.3 过期时间与常用全局命令

```bash
> expire key 60          # 设置过期时间（秒）
> ttl key                # 剩余秒数（-1 永不过期，-2 已不存在）
> del key                # 删除
> exists key             # 是否存在（1/0）
> keys user:*            # 模糊查询（生产禁用，O(n) 伤性能）
> type key               # 查看类型
> flushall               # 清空全部（慎用！）
```

## 12.3 Java 集成方式

### 12.3.1 Jedis（轻量客户端）

```xml
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>5.1.3</version>
</dependency>
```

```java
package com.example.redisdemo;

import redis.clients.jedis.Jedis;

public class JedisDemo {

    public static void main(String[] args) {
        // 1. 连接 Redis（默认 localhost:6379）
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            // 2. 基础操作（API 与命令行一一对应）
            jedis.set("name", "张三");
            System.out.println("name = " + jedis.get("name"));

            // 3. 带过期时间：验证码场景
            jedis.setex("code:13812345678", 300, "123456");
            System.out.println("剩余时间 = " + jedis.ttl("code:13812345678"));

            // 4. Hash
            jedis.hset("user:1", "name", "张三");
            jedis.hset("user:1", "age", "20");
            System.out.println("age = " + jedis.hget("user:1", "age"));

            // 5. 自增（点赞）
            jedis.incr("like:1001");
            jedis.incr("like:1001");
            System.out.println("点赞数 = " + jedis.get("like:1001"));

            // 6. 设置密码时连接（配置了 requirepass 才需要）
            // jedis.auth("你的密码");
        }
    }
}
```

> Jedis 线程不安全，多线程需用连接池 `JedisPool`；Lettuce 线程安全，是 Spring Data Redis 的默认客户端。

### 12.3.2 Spring Boot 整合（重点）

**依赖：**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<!-- 连接池需要 -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

**配置（application.yml）：**

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password:                 # 无密码留空
      database: 0               # 默认 0 号库（最多 16 个）
      timeout: 3000ms           # 连接超时
      lettuce:
        pool:
          max-active: 8         # 最大连接数
          max-idle: 8           # 最大空闲连接
          min-idle: 0           # 最小空闲连接
```

**RedisTemplate 序列化配置（重要）：**

默认的 `RedisTemplate` 使用 JDK 序列化，存进去是二进制乱码、key 带 `\xAC\xED...` 前缀。必须配置 Jackson JSON 序列化：

```java
package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key 用 String 序列化（避免 \xAC\xED 乱码）
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value 用 JSON 序列化
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
```

**使用示例（RedisService）：**

```java
package com.example.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 写入（带过期时间）
    public void set(String key, Object value, long seconds) {
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
    }

    // 读取
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 删除
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    // 自增（原子操作，可用于计数器）
    public long incr(String key) {
        Long val = redisTemplate.opsForValue().increment(key);
        return val == null ? 0 : val;
    }

    // Hash 操作：存对象属性
    public void hset(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    // 分布式锁：setnx（不存在才设置成功），返回 true 表示拿到锁
    public boolean tryLock(String key, String value, long seconds) {
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(key, value, seconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    public static void main(String[] args) {
        System.out.println("示例见 Spring Boot 项目的 RedisService 测试类");
    }
}
```

## 12.4 缓存实战：三大问题（面试核心）

### 12.4.1 标准缓存读取流程

```java
package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class UserCacheService {

    @Autowired
    private StringRedisTemplate redis;   // key/value 都是 String，配合 JSON 字符串使用

    @Autowired
    private UserMapper userMapper;

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 读多写少的用户查询：先查缓存，未命中查库并回填
     */
    public User findById(Long id) throws JsonProcessingException {
        String key = "user:" + id;

        // ① 查缓存
        String json = redis.opsForValue().get(key);
        if (json != null) {
            // ② 命中直接返回（JSON 反序列化）
            return JSON.readValue(json, User.class);
        }

        // ③ 未命中 → 查数据库
        User user = userMapper.findById(id);
        if (user == null) {
            return null;
        }

        // ④ 写回缓存，并设置过期时间（防止缓存永久占用）
        redis.opsForValue().set(key, JSON.writeValueAsString(user), 30, TimeUnit.MINUTES);
        return user;
    }
}
```

### 12.4.2 缓存穿透

**问题**：查询一个**不存在的 key**（如 `user:999999`），缓存没有、数据库也没有。攻击者不断伪造不存在的 id 请求，缓存永远不命中，压力全打给数据库。

```
请求不存在的 id ──► 缓存没有 ──► 数据库也没有（每次请求都打库）──► 数据库被打垮
```

**解决方案**：

| 方案 | 说明 | 代码 |
| --- | --- | --- |
| ① 缓存空值 | 查库无果也缓存一个空值，加短过期时间（如 5 分钟） | 见下方代码 |
| ② 布隆过滤器 | 请求前先过布隆过滤器，不存在直接拒绝（大数据量推荐） | 概念了解即可 |

```java
// 解决穿透：查不到也缓存空值
public User findByIdSafe(Long id) throws JsonProcessingException {
    String key = "user:" + id;
    String json = redis.opsForValue().get(key);
    if (json != null) {
        // 约定：空值缓存存 "" 表示"数据库中不存在"
        return json.isEmpty() ? null : JSON.readValue(json, User.class);
    }

    User user = userMapper.findById(id);
    if (user == null) {
        // 缓存空值，5 分钟过期，防止被反复穿透
        redis.opsForValue().set(key, "", 5, TimeUnit.MINUTES);
        return null;
    }

    redis.opsForValue().set(key, JSON.writeValueAsString(user), 30, TimeUnit.MINUTES);
    return user;
}
```

### 12.4.3 缓存击穿

**问题**：某个**热点 key** 突然过期（比如秒杀商品详情），同一瞬间大量请求同时发现缓存没有 → 全部打到数据库，数据库瞬间被打垮。

**解决方案**：

| 方案 | 说明 |
| --- | --- |
| ① 互斥锁（推荐） | 只有一个线程去查库回填，其他线程等待/自旋重试 |
| ② 逻辑过期 | key 永不过期，value 里带过期时间，后台异步刷新（适合高并发读多） |

```java
// 解决击穿：互斥锁（只允许一个线程查库回填）
public User findByIdMutex(Long id) throws JsonProcessingException {
    String key = "user:" + id;
    String lockKey = "lock:user:" + id;

    String json = redis.opsForValue().get(key);
    if (json != null) {
        return json.isEmpty() ? null : JSON.readValue(json, User.class);
    }

    // 拿分布式锁（setnx 带过期时间，防止持锁线程挂掉死锁）
    Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
    if (Boolean.TRUE.equals(locked)) {
        try {
            // 拿到锁：二次检查（double check，防止等待线程重复查库）
            json = redis.opsForValue().get(key);
            if (json != null) {
                return json.isEmpty() ? null : JSON.readValue(json, User.class);
            }
            User user = userMapper.findById(id);
            redis.opsForValue().set(key,
                    user == null ? "" : JSON.writeValueAsString(user), 30, TimeUnit.MINUTES);
            return user;
        } finally {
            redis.delete(lockKey);   // 释放锁
        }
    }

    // 没拿到锁：短暂等待后重试（简单演示，可加最大重试次数）
    try { Thread.sleep(50); } catch (InterruptedException ignored) { }
    return findByIdMutex(id);
}
```

### 12.4.4 缓存雪崩

**问题**：**大量 key 在同一时间段集中过期**（如所有缓存统一 1 小时过期），或者 Redis 宕机，导致大批请求同时打到数据库，数据库崩溃。

**解决方案**：

| 方案 | 说明 |
| --- | --- |
| ① 过期时间加随机值 | 不同 key 的过期时间错开：30min + random(0~5min) |
| ② 多级缓存 | 本地缓存（Caffeine）+ Redis + DB |
| ③ 高可用 | Redis 集群 + 哨兵，宕机自动切换 |

```java
// 解决雪崩：过期时间加随机值，错峰过期
private long randomExpire() {
    // 基础 30 分钟 + 0~5 分钟随机，避免同批 key 同时过期
    return 30 * 60 + (long) (Math.random() * 5 * 60);
}

public User findById(String key, Long id) throws JsonProcessingException {
    User user = userMapper.findById(id);
    if (user != null) {
        redis.opsForValue().set("user:" + id, JSON.writeValueAsString(user), randomExpire(), TimeUnit.SECONDS);
    }
    return user;
}
```

### 12.4.5 三大问题速记表

| 问题 | 一句话描述 | 核心解决 |
| --- | --- | --- |
| **穿透** | 查一个**根本不存在**的数据 | 缓存空值 / 布隆过滤器 |
| **击穿** | 一个**热点 key** 过期瞬间 | 互斥锁 / 逻辑过期 |
| **雪崩** | **大量 key 同时过期**或 Redis 挂了 | 随机过期时间 / 多级缓存 / 集群 |

## 12.5 实战：验证码存储（StringRedisTemplate）

配合第八章邮件/短信验证码场景，验证码存 Redis 更规范（自带过期，天然防刷）：

```java
package com.example.demo.controller;

import com.example.demo.service.RedisCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Random;

@RestController
@RequestMapping("/code")
public class CodeController {

    @Autowired
    private RedisCodeService codeService;

    // GET /code/send?phone=13812345678
    @GetMapping("/send")
    public String send(@RequestParam String phone) {
        // 防刷：60 秒内不能重复发送
        if (codeService.exists("code:send:" + phone)) {
            return "发送过于频繁，请稍后再试";
        }
        String code = String.format("%06d", new Random().nextInt(1000000));
        codeService.saveCode(phone, code);
        // 生产环境在这里调用短信接口发送（第八章邮件同理）
        System.out.println("验证码 [" + phone + "] = " + code);
        return "验证码已发送";
    }

    // GET /code/verify?phone=13812345678&code=123456
    @GetMapping("/verify")
    public String verify(@RequestParam String phone, @RequestParam String code) {
        return codeService.verify(phone, code) ? "验证成功" : "验证码错误或已过期";
    }
}
```

```java
package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisCodeService {

    @Autowired
    private StringRedisTemplate redis;

    // 保存验证码：5 分钟有效 + 60 秒发送间隔
    public void saveCode(String phone, String code) {
        redis.opsForValue().set("code:" + phone, code, 5, TimeUnit.MINUTES);
        redis.opsForValue().set("code:send:" + phone, "1", 60, TimeUnit.SECONDS);
    }

    // 校验并删除（一次性）
    public boolean verify(String phone, String code) {
        String key = "code:" + phone;
        String right = redis.opsForValue().get(key);
        if (right != null && right.equals(code)) {
            redis.delete(key);          // 校验成功立即作废，防止重放
            return true;
        }
        return false;
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }
}
```

## 12.6 实战：分布式锁

### 12.6.1 为什么需要分布式锁

多台服务器（集群）抢同一资源（如库存扣减、秒杀），`synchronized` 只锁单机 JVM，跨机器无效，必须用 Redis 做**跨进程锁**：

```java
package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class StockService {

    @Autowired
    private StringRedisTemplate redis;

    /**
     * 扣减库存（简单演示）：同一时刻只有一个线程/机器能扣
     */
    public boolean deduct(int userId) {
        // 锁的 key + 唯一 value（防止误删别人的锁）
        String lockKey = "lock:stock:iphone";
        String requestId = UUID.randomUUID().toString();   // 每线程唯一

        // setnx：key 不存在才成功（获取锁），10 秒自动过期防死锁
        Boolean locked = redis.opsForValue()
                .setIfAbsent(lockKey, requestId, 10, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            return false;    // 没拿到锁：别人正在扣
        }

        try {
            // 业务：检查库存并扣减（这里模拟）
            String stock = redis.opsForValue().get("stock:iphone");
            int num = stock == null ? 0 : Integer.parseInt(stock);
            if (num <= 0) return false;
            redis.opsForValue().set("stock:iphone", String.valueOf(num - 1));
            System.out.println("用户 " + userId + " 扣减成功，剩余 " + (num - 1));
            return true;
        } finally {
            // 释放锁：Lua 脚本保证"判断是自己的锁 + 删除"是原子的
            // 简化演示用先判断再删，生产必须用 Lua（下方给出）
            String lua = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end";
            redis.execute(org.springframework.data.redis.core.script.DefaultRedisScript
                    .of(lua, Long.class), java.util.List.of(lockKey), requestId);
        }
    }
}
```

> **要点**：value 用 UUID 是为了**只删自己的锁**（防止 A 的锁过期后 B 拿到锁，A 回来把 B 的锁删了）。释放用 **Lua 脚本**保证"比较 + 删除"两步原子执行。生产更成熟方案：Redisson（自带看门狗续期）。

## 12.7 持久化：RDB 与 AOF（面试题）

| 方案 | 原理 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **RDB**（默认） | 定时把内存数据做**快照**存 dump.rdb | 文件小、恢复快 | 可能丢最近几分钟数据 |
| **AOF** | 把每条**写命令**追加到 .aof 文件 | 最多丢 1 秒数据 | 文件大、恢复慢 |

**配置（redis.conf）：**

```conf
# RDB：900 秒内至少 1 次写操作就触发快照
save 900 1
save 300 10
save 60 10000

# AOF：开启 + 每秒同步一次（性能与安全的折中）
appendonly yes
appendfsync everysec
```

> **面试答法**：生产一般 **RDB + AOF 同时开启**，AOF 保证不丢数据，RDB 用于快速恢复。

## 12.8 小结与练习

**本章重点**：
- 五种数据类型：String / Hash / List / Set / ZSet 及各自场景
- Spring Boot 整合：`StringRedisTemplate` + RedisConfig 序列化配置
- 缓存三大问题：穿透（空值缓存）、击穿（互斥锁）、雪崩（随机过期）
- 分布式锁：setnx + UUID + Lua 释放
- 持久化：RDB 快照 + AOF 日志

**面试题参考**：
1. Redis 为什么快？（内存 + 单线程 + IO 多路复用）
2. 缓存穿透/击穿/雪崩的区别与解决？
3. Redis 分布式锁的实现？为什么用 Lua 脚本释放？
4. RDB 与 AOF 的区别？
5. 5 种数据类型的底层结构与使用场景？

**课后练习**：
1. 用命令行向 Redis 写入用户信息（Hash）、点赞数（String）、排行榜（ZSet），分别用 `hgetall`、`incr`、`zrevrange` 验证。
2. 为第九章的用户查询接口加缓存（JSON 序列化），首次查询后再次访问对比响应时间。
3. 实现验证码登录：发验证码（Redis 5 分钟过期 + 60 秒防刷）、校验登录。
4. 模拟并发扣库存（100 个线程扣 10 件库存），验证分布式锁生效。
5. 研究：用 `@Cacheable` 注解实现缓存（Spring Cache 抽象），与手写对比。

上一章：[11-日志框架.md](./11-日志框架.md) | 下一章：[13-SpringSecurity.md](./13-SpringSecurity.md) | 返回目录：[README.md](./README.md)
