# 第二十三章 Zookeeper 分布式协调服务

> 本章目标：理解 Zookeeper 是什么、解决分布式系统的哪些问题，掌握数据模型、ZAB 协议、选举原理，会用 Java API 与 Curator 实现分布式锁、服务注册发现、配置中心三大经典场景。
>
> 前置知识：第五章多线程、第十二章 Redis 分布式锁、第十五章微服务（Nacos 底层依赖 ZK 同类技术）。

## 23.1 为什么需要 Zookeeper

### 23.1.1 分布式系统的协调难题

单机时代没有协调问题：一个进程自己说了算。分布式时代，多个节点需要"商量着办"：

| 分布式难题 | 描述 | 没有协调器的后果 |
| --- | --- | --- |
| 选主（Leader 选举） | 多台机器谁干活？ | 都干活 → 重复执行；都不干 → 服务停摆 |
| 分布式锁 | 同一资源不能并发操作 | 库存超卖、重复扣款 |
| 服务注册发现 | 服务实例地址怎么互相知道 | 写死 IP，扩缩容全乱 |
| 配置管理 | 配置改了怎么通知所有节点 | 逐台改，容易漏 |
| 集群元数据 | 副本、分区信息存哪 | 没有权威数据源 |

### 23.1.2 Zookeeper 是什么

```
Zookeeper = 分布式协调服务（动物园管理员）
      │
  ┌───┴───┬─────────┬──────────┐
  │       │         │          │
选主     分布式锁   注册中心   配置中心   发布订阅
```

Zookeeper 是 Apache 顶级项目，最初为 Hadoop 生态开发，特点是：

1. **一致性**：所有节点数据强一致（ZAB 协议保证）
2. **高可用**：集群半数以上存活即可服务
3. **高性能**：读多写少场景，读写都能到十万级 QPS
4. **简单**：像一个分布式文件系统（目录树 + 节点），API 只有 9 个

> 认知：ZK 本身不存业务数据，它是"协调员"，数据量小（K/V），贵在**一致性和实时通知**。

### 23.1.3 Zookeeper vs 同类技术

| 对比 | Zookeeper | etcd | Nacos / Consul |
| --- | --- | --- | --- |
| 核心 | 分布式协调 | KV 存储 + Watch | 注册中心 + 配置中心 |
| 一致性协议 | ZAB | Raft | Raft / 自研 |
| 语言 | Java | Go | Java / Go |
| 应用场景 | 分布式锁、选主、Dubbo | K8s 集群存储 | 微服务注册配置 |
| 学习价值 | 原理经典，面试必问 | 了解即可 | 实际开发用得多 |

> 第十五章的 Nacos 就是"ZK + 配置中心 + 注册中心"的升级版。**先学懂 ZK 原理，再学 Nacos 事半功倍。**

## 23.2 安装与基本操作

### 23.2.1 Docker 单机启动

```bash
docker run -d --name zk \
  -p 2181:2181 \
  -e ZOO_MY_ID=1 \
  zookeeper:3.8
```

### 23.2.2 集群启动（docker-compose）

```yaml
# docker-compose.yml：3 台组成集群
services:
  zk1:
    image: zookeeper:3.8
    ports: ["2181:2181"]
    environment:
      ZOO_MY_ID: 1
      ZOO_SERVERS: server.1=zk1:2888:3888;2181 server.2=zk2:2888:3888;2181 server.3=zk3:2888:3888;2181
  zk2:
    image: zookeeper:3.8
    ports: ["2182:2181"]
    environment:
      ZOO_MY_ID: 2
      ZOO_SERVERS: server.1=zk1:2888:3888;2181 server.2=zk2:2888:3888;2181 server.3=zk3:2888:3888;2181
  zk3:
    image: zookeeper:3.8
    ports: ["2183:2181"]
    environment:
      ZOO_MY_ID: 3
      ZOO_SERVERS: server.1=zk1:2888:3888;2181 server.2=zk2:2888:3888;2181 server.3=zk3:2888:3888;2181
```

### 23.2.3 命令行操作（zkCli）

```bash
# 进入命令行客户端
docker exec -it zk ./bin/zkCli.sh -server localhost:2181

# 常用命令
ls /                    # 查看根节点下所有子节点
create /demo "hello"    # 创建节点（持久节点）
get /demo               # 读取节点数据
set /demo "world"       # 修改节点数据
delete /demo            # 删除节点（无子节点时）
rmr /demo               # 递归删除
stat /demo              # 节点状态信息
```

输出示例：

```
[zk: localhost:2181(CONNECTED) 1] create /demo "hello"
Created /demo
[zk: localhost:2181(CONNECTED) 2] get /demo
hello
[zk: localhost:2181(CONNECTED) 3] ls /
[demo, zookeeper]
```

## 23.3 数据模型（Znode）

### 23.3.1 树形结构

```
/（根节点）
├── /demo
│   ├── /demo/user-service     ← 每个节点叫 Znode
│   │   ├── /demo/user-service/0000000001
│   │   └── /demo/user-service/0000000002
│   └── /demo/order-service
└── /zookeeper                 ← 系统自带，存选举等元数据
```

每个 Znode 三要素：**路径**（唯一）、**数据**（1MB 内）、**状态**（stat：版本号、时间戳、数据大小等）。

### 23.3.2 节点类型（面试必问）

| 类型 | 持久性 | 序号 | 说明 |
| --- | --- | --- | --- |
| 持久节点（PERSISTENT） | 创建后一直存在 | 无 | 存配置、元数据 |
| 持久顺序节点（PERSISTENT_SEQUENTIAL） | 一直存在 | 带递增序号 | 分布式锁基础 |
| 临时节点（EPHEMERAL） | 会话断开自动删除 | 无 | 服务注册（宕机自动下线） |
| 临时顺序节点（EPHEMERAL_SEQUENTIAL） | 会话断开删除 | 带递增序号 | **分布式锁核心** |

```bash
create -e /demo/ephemeral-node "临时"          # -e 临时节点
create -s /demo/seq-node "顺序"                # -s 顺序节点：生成 /demo/seq-node0000000001
create -e -s /demo/lock "锁"                   # 临时顺序节点
```

> **临时节点为什么重要？** 客户端挂了，临时节点自动消失 → 服务宕机自动从注册中心下线、锁自动释放，**不需要人工清理**。这就是 ZK 做注册中心和分布式锁的最大优势。

### 23.3.3 Watch 监听机制（核心特性）

```bash
get /demo watch    # 注册监听：数据一变就通知
ls /demo watch     # 注册监听：子节点一变就通知
```

- 一次监听**只触发一次**，触发后需重新注册
- 触发时机：数据变化、子节点变化、节点删除、节点创建

```
客户端 A                          Zookeeper
   │ ── get /config watch ──►        │
   │                                 │
   │ ◄──── 数据变更通知 ──────        │   ← 其他客户端改了 /config
   │ ── get /config watch ──►        │   ← 重新注册（一次性）
   │                                 │
```

> 企业里"配置中心"就靠这个：配置变了，所有订阅的节点实时收到通知，自动刷新（对应 Nacos 的动态刷新原理）。

## 23.4 ZAB 协议与 Leader 选举

### 23.4.1 集群角色

```
        ┌───────── 选举 ─────────┐
        ▼                       ▼
    [Leader]              [Follower] × N
      │ 写请求统一走 Leader ──────► 同步给 Follower
      │
  [Observer]（可选）：只读，不参与选举投票，用于扩展读能力
```

| 角色 | 职责 |
| --- | --- |
| Leader | 唯一写入口，广播事务，分发提案 |
| Follower | 处理读请求，参与选举投票，转发写请求给 Leader |
| Observer | 只读节点，不投票，水平扩展读性能 |

### 23.4.2 ZAB 协议：两阶段提交

```
写请求 ──► Leader 生成提案（ZXID 递增）
              │
   ┌──────────┼────────────┐
   ▼          ▼            ▼
Follower1   Follower2    Follower3
   │ ACK       │ ACK        │ ACK
   └───────────┼────────────┘
               ▼
     收到"多数派"ACK（> n/2）
               │
               ▼
   Leader 广播 COMMIT，各节点提交
```

- **写流程**：客户端 → Leader → 广播提案 → 半数 ACK → COMMIT
- **读流程**：任意节点直接返回（本地数据），读不经过 Leader
- **强一致**：半数节点提交成功才算成功，因此必须部署**奇数台**（3 或 5 台）

> 为什么必须奇数台？容忍 1 台故障要 3 台，容忍 2 台故障要 5 台。偶数台（如 4 台）故障容忍度和 3 台一样，还多花钱。

### 23.4.3 Leader 选举（崩溃恢复）

```
节点 1（旧 Leader）挂了
      │
节点 2、3 发现连接断开 ──► 进入选举
      │
各节点投票：先投自己，再投 ZXID 最大 / SID 最大的节点
      │
节点 3 获得多数票 ──► 成为新 Leader ──► 同步数据后对外服务
```

**投票规则**（以 myid、zxid 为维度）：
1. 优先选 **ZXID（事务 ID）最大**的节点（数据最新）
2. ZXID 相同，选 **myid（节点编号）最大**的节点

选举期间集群**不可写**（短暂不可用），选完恢复。3 台集群挂 1 台还能选出来，挂 2 台就选举失败（无法满足多数派）。

## 23.5 Java 原生 API

### 23.5.1 依赖

```xml
<dependency>
    <groupId>org.apache.zookeeper</groupId>
    <artifactId>zookeeper</artifactId>
    <version>3.8.3</version>
</dependency>
```

### 23.5.2 基础操作

```java
public class ZkDemo {

    public static void main(String[] args) throws Exception {
        // 1. 连接（5000ms 超时）
        ZooKeeper zk = new ZooKeeper("localhost:2181", 5000, event ->
                System.out.println("收到事件：" + event.getType()));

        // 2. 创建节点：持久节点
        zk.create("/demo", "hello".getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,           // 权限：完全开放
                CreateMode.PERSISTENT);

        // 3. 创建临时顺序节点（分布式锁会用到）
        String path = zk.create("/demo/lock-", "".getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println("创建节点：" + path);   // /demo/lock-0000000001

        // 4. 读取数据
        byte[] data = zk.getData("/demo", false, null);
        System.out.println("数据：" + new String(data));

        // 5. 注册监听（Watch）
        zk.getData("/demo", watchedEvent ->
                System.out.println("节点被修改：" + watchedEvent.getType()), null);

        // 6. 修改数据（会触发上面的监听）
        zk.setData("/demo", "world".getBytes(), -1);   // -1 = 不校验版本

        // 7. 删除
        zk.delete("/demo/lock-0000000001", -1);
        zk.delete("/demo", -1);

        zk.close();
    }
}
```

### 23.5.3 连接状态

| 状态 | 含义 |
| --- | --- |
| CONNECTING | 正在连接 |
| CONNECTED | 连接成功 |
| RECONNECTED | 断线后重连成功（ZooKeeper 内部自动处理） |
| EXPIRED | 会话过期（必须重新建连接） |

> **注意**：客户端断线 ≠ 会话过期。默认 sessionTimeout（如 30s）内重连上，临时节点还在；超时未连上，临时节点才被删除。这让短暂网络抖动不影响注册的临时节点。

## 23.6 Curator 框架（企业实际使用）

原生 API 要自己处理 Watch 重注册、重连，非常繁琐。**Curator** 是 Netflix 开源的封装，企业基本都用它。

### 23.6.1 依赖

```xml
<dependency>
    <groupId>org.apache.curator</groupId>
    <artifactId>curator-framework</artifactId>
    <version>5.5.0</version>
</dependency>
<dependency>
    <groupId>org.apache.curator</groupId>
    <artifactId>curator-recipes</artifactId>   <!-- 分布式锁等高级组件 -->
    <version>5.5.0</version>
</dependency>
```

### 23.6.2 建立客户端

```java
RetryPolicy retryPolicy = new ExponentialBackoffRetry(
        1000,    // 初始等待 1 秒
        3);      // 最多重试 3 次（指数退避：1s → 2s → 4s）

CuratorFramework client = CuratorFrameworkFactory.builder()
        .connectString("localhost:2181")
        .sessionTimeoutMs(30000)
        .connectionTimeoutMs(5000)
        .retryPolicy(retryPolicy)
        .build();
client.start();
```

> RetryPolicy 是 Curator 的灵魂：**连接不上会自动重连**，这是原生 API 没有的。三种策略：`ExponentialBackoffRetry`（指数退避，推荐）、`RetryNTimes`（固定次数）、`RetryForever`（永远重试）。

### 23.6.3 简洁 API

```java
// 创建（递归创建父节点）
client.create().creatingParentsIfNeeded()
        .forPath("/demo/user-service/instance-1", "192.168.1.10:8080".getBytes());

// 读取
byte[] data = client.getData().forPath("/demo/user-service/instance-1");

// 修改
client.setData().forPath("/demo/user-service/instance-1", "新值".getBytes());

// 删除（递归删除子节点）
client.delete().deletingChildrenIfNeeded().forPath("/demo");
```

## 23.7 实战一：分布式锁

### 23.7.1 为什么不能用 JVM 锁

```java
// 单机没问题：JVM 内的锁
synchronized (this) { 扣库存(); }

// 分布式下：多个 JVM 各自一把锁，照样超卖！
// 服务器 A 的 synchronized 管不住服务器 B 的线程
```

需要**跨进程的锁**：所有请求都去 ZK 上争抢一个"锁节点"。

### 23.7.2 实现原理（临时顺序节点 + Watch）

```
加锁流程：
1. 在 /locks 下创建临时顺序节点：/locks/lock-0000000001
2. 获取 /locks 下所有子节点，排序
3. 自己是不是最小的？是 → 拿到锁
   不是 → 监听前一个节点，等它删除
4. 前一个节点删了（释放锁），被唤醒，重新检查
5. 释放锁：删除自己的节点

锁释放流程（自动）：
- 主动释放：删除节点
- 宕机释放：临时节点随会话断开自动消失
```

```
/locks/lock-0000000001  ← 客户端 A（最小，持有锁）
/locks/lock-0000000002  ← 客户端 B（监听 1 号）
/locks/lock-0000000003  ← 客户端 C（监听 2 号）
```

**相比 Redis 锁（第十二章）的优势**：临时节点自动释放，**没有过期时间误删问题**，天然防死锁。

### 23.7.3 Curator 实现（三行代码）

```java
public class ZkLockDemo {

    public static void main(String[] args) throws Exception {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString("localhost:2181")
                .retryPolicy(retryPolicy)
                .build();
        client.start();

        // 创建分布式锁（InterProcessMutex = 可重入互斥锁）
        InterProcessMutex lock = new InterProcessMutex(client, "/locks/order-lock");

        // 业务代码
        try {
            if (lock.acquire(5, TimeUnit.SECONDS)) {   // 5 秒内拿不到锁就放弃
                try {
                    // 临界区：扣库存 / 下单
                    System.out.println("拿到锁，执行业务...");
                    Thread.sleep(2000);
                } finally {
                    lock.release();   // 释放锁（等价于删除节点）
                }
            } else {
                System.out.println("获取锁超时");
            }
        } finally {
            client.close();
        }
    }
}
```

### 23.7.4 乐观锁思路（版本号）

ZK 的 `setData` 带版本号校验，版本不匹配抛 `BadVersionException`，可实现"CAS"效果：

```java
// 第一次读，拿到版本号
Stat stat = new Stat();
byte[] data = client.getData().storingStatIn(stat).forPath("/stock/count");
int version = stat.getVersion();

// 修改时带版本号：被别人改过就失败，重试
try {
    client.setData().withVersion(version)
            .forPath("/stock/count", "98".getBytes());
} catch (BadVersionException e) {
    // 版本冲突，说明别人先改了，重新读取再试
    System.out.println("版本冲突，重试");
}
```

## 23.8 实战二：服务注册与发现

### 23.8.1 架构

```
服务提供者 A（启动时）           Zookeeper                 消费者
      │ 注册：/services/order/instance-1 ──► │
      │                                      │ ◄── 订阅 /services/order（Watch）
      │ 心跳（会话保活）                     │      有新实例上线/下线立刻感知
      │                                      │
      └── 宕机：临时节点自动消失 ────────────►│──► 通知消费者
```

### 23.8.2 注册（服务提供者）

```java
@Service
public class RegisterService {

    private final CuratorFramework client;

    public void register(String serviceName, String address) throws Exception {
        String path = "/services/" + serviceName + "/instance-";
        // 临时顺序节点：宕机自动下线，顺序号区分多实例
        client.create().creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(path, address.getBytes());
        System.out.println("服务已注册：" + serviceName + " -> " + address);
    }
}
```

### 23.8.3 发现（消费者）

```java
@Component
public class DiscoveryService {

    private final CuratorFramework client;

    public List<String> getInstances(String serviceName) throws Exception {
        // 拿到所有实例节点
        List<String> children = client.getChildren()
                .forPath("/services/" + serviceName);

        List<String> addresses = new ArrayList<>();
        for (String child : children) {
            byte[] data = client.getData()
                    .forPath("/services/" + serviceName + "/" + child);
            addresses.add(new String(data));
        }
        return addresses;   // 如 [192.168.1.10:8080, 192.168.1.11:8080]
    }
}
```

> 这就是 Dubbo 早期的注册中心原理（现在 Dubbo 也支持 Nacos）。**Nacos/Consul 就是"ZK 这套机制 + 更多功能"的产品化**，理解了本节的临时节点 + Watch，注册中心就全懂了。

## 23.9 实战三：配置中心

```java
@Component
public class ConfigCenter {

    private final CuratorFramework client;

    // 启动时读取配置
    public String getConfig(String key) throws Exception {
        return new String(client.getData().forPath("/config/" + key));
    }

    // 监听配置变化：改了自动通知（配置中心核心）
    public void watchConfig(String key) {
        NodeCache cache = new NodeCache(client, "/config/" + key);
        cache.getListenable().addListener(() ->
                System.out.println("配置变更：" + key + " = " +
                        new String(cache.getCurrentData().getData())));
        cache.start();
        // 之后其他客户端 setData，这里自动触发回调
    }
}
```

使用：

```bash
# 运维/开发修改配置
create /config/db-url "jdbc:mysql://192.168.1.10:3306/db1"
set /config/db-url "jdbc:mysql://192.168.1.11:3306/db2"
```

所有监听该节点的服务**实时刷新**，无需重启。这就是配置中心的本质（对应 Nacos Config、Spring Cloud Config 原理）。

## 23.10 小结与练习

**本章重点**：
- ZK = 分布式协调：选主 / 分布式锁 / 注册中心 / 配置中心
- 数据模型：树形 Znode，四类节点（持久/持久顺序/临时/临时顺序）
- **临时节点 + Watch** = 两大核心武器（自动清理 + 实时通知）
- ZAB 协议：写走 Leader 两阶段提交，半数 ACK 才提交；集群必须奇数台
- Leader 选举：ZXID 大者优先，相同则 myid 大者
- Curator：企业标配，自带重连重试；`InterProcessMutex` 三行代码实现分布式锁
- 分布式锁对比：ZK（自动释放、无过期误删）vs Redis（性能高、有锁过期问题）

**面试题参考**：
1. 什么是 Zookeeper？解决什么问题？
2. ZK 有哪些节点类型？临时节点有什么用？
3. ZK 如何保证数据一致性？（ZAB 两阶段提交 + 半数机制）
4. 为什么 ZK 集群要奇数台？
5. Leader 挂了怎么办？（选举流程）
6. 讲一下 ZK 分布式锁的实现原理？（临时顺序节点 + Watch 前一个节点）
7. ZK 分布式锁和 Redis 分布式锁的区别？
8. 用 ZK 做注册中心，服务宕机怎么下线？（临时节点随会话消失）

**课后练习**：
1. 用 Docker 启动 3 台 ZK 组成集群，杀掉 Leader 观察自动选举恢复。
2. 用 zkCli 创建四种类型节点，验证临时节点在会话断开后自动消失。
3. 用 Curator 实现一个分布式锁，起两个线程/进程争抢，打印拿锁顺序。
4. 模拟服务注册与发现：注册两个实例，用 Watch 观察新增/下线通知。
5. 做一个配置中心 Demo：一个程序监听 `/config`，另写代码改配置，观察实时刷新。

上一章：[22-Nginx.md](./22-Nginx.md) | 下一章：[24-JVM调优.md](./24-JVM调优.md) | 返回目录：[README.md](./README.md)
