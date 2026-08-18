# 第二十七章 Kafka 分布式消息队列

> 本章目标：理解 Kafka 的架构与设计哲学（日志追加、分区并行、顺序读写），掌握核心概念（Broker/Topic/Partition/Offset/消费者组），能完成 Spring Boot 整合，并理解可靠性、顺序性、消息堆积等生产级问题。
>
> 前置知识：第十六章 RabbitMQ（消息队列基础）、第二十六章 Netty（网络模型可对比）。

## 27.1 为什么用 Kafka

### 27.1.1 Kafka vs RabbitMQ（选型对比）

| 对比 | RabbitMQ | Kafka |
| --- | --- | --- |
| 定位 | 通用消息队列，功能全面 | **分布式流处理平台**，吞吐优先 |
| 吞吐量 | 万级/秒 | **百万级/秒** |
| 消息模型 | 消费即删除 | **日志追加，可重放** |
| 延迟 | 微秒~毫秒 | 毫秒级 |
| 消息顺序 | 单队列有序 | **分区内有序** |
| 堆积能力 | 一般 | **极强（磁盘顺序写）** |
| 复杂路由 | 四类交换机很灵活 | Topic 简单路由 |
| 典型场景 | 业务解耦、异步、RPC 结果 | **大数据、日志采集、流计算、削峰** |

**选型结论**（呼应 16.1 的选型表）：
- 业务消息、需要复杂路由/死信 → **RabbitMQ**
- 大数据量、日志、流处理、超高吞吐 → **Kafka**
- 已用阿里系、需要事务消息 → **RocketMQ**

### 27.1.2 Kafka 为什么快（面试必问）

1. **顺序写磁盘**：消息追加到日志文件尾部，磁盘顺序写 ≈ 内存速度（省去随机寻道）
2. **页缓存（PageCache）**：读写走操作系统页缓存，不频繁刷盘
3. **零拷贝**：`sendfile` 系统调用，数据直接从磁盘 → Socket，**不经过用户态拷贝**（呼应第二十六章零拷贝）
4. **批量 + 压缩**：生产者批量发送、批量压缩（LZ4/ZSTD）
5. **分区并行**：Topic 拆多个 Partition，多消费者并行拉取

## 27.2 核心架构与概念

### 27.2.1 架构图

```
                     ┌──────────────────────────┐
生产者也 Producers ──►│  Broker 集群（Kafka 服务器） │
                     │  ┌───────┐ ┌───────┐      │
                     │  │Broker1 │ │Broker2 │ ... │
                     │  │ Topic-A │ │ Topic-A │   │
                     │  │ P0(Leader)│P1(Leader)  │
                     │  │ P1(Follower)│P0(Follower)│
                     │  └───────┘ └───────┘      │
                     └──────────┬───────────────┘
                                ▼
                    消费者组 Consumer Group
                    (组内成员分担分区，组间相互独立)
```

### 27.2.2 核心概念

| 概念 | 说明 | 类比 |
| --- | --- | --- |
| **Broker** | Kafka 服务器节点（集群由多个 Broker 组成） | 餐馆 |
| **Topic** | 消息分类（逻辑概念） | 菜名 |
| **Partition** | Topic 的物理分片，消息存在分区里 | 灶台（多口灶并行炒菜） |
| **Offset** | 消息在分区内的序号（从 0 递增） | 排队号 |
| **Replica** | 分区副本（Leader 读写，Follower 备份） | 备菜师傅 |
| **Consumer Group** | 消费者组：组内消费者分摊分区 | 一桌客人的多把筷子 |
| **Zookeeper/KRaft** | 协调器：存元数据、选举 Controller（新版用 KRaft 替代 ZK） | 经理 |

### 27.2.3 分区与消费者组（核心理解）

```
Topic：orders（3 个分区）
┌──────────┬──────────┬──────────┐
│ P0 │ P1 │ P2 │
└──────────┴──────────┴──────────┘
   │        │        │
   ▼        ▼        ▼
  消费者组 A：C1-C3   （3 个消费者，一人一个分区，并行消费）
  消费者组 B：C1-C2   （2 个消费者，C1 消费 P0+P1，C2 消费 P2）

规则：
1. 组内消费者数 > 分区数 → 多出的消费者闲置（浪费！）
2. 组内消费者数 < 分区数 → 一个消费者消费多个分区
3. 不同消费者组互不影响（广播：每个组都能收到全量消息）
```

> **面试高频**：分区内有序、分区见无序。要让同一用户的消息有序 → 按 `key` 哈希路由到同一分区。

## 27.3 安装与命令行操作

### 27.3.1 Docker 快速启动

```bash
# 单机版（KRaft 模式，无需 Zookeeper，Kafka 3.x+）
docker run -d --name kafka \
  -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  apache/kafka:3.7
```

### 27.3.2 常用命令

```bash
# 进入容器操作
docker exec -it kafka bash

# 创建 Topic（3 分区 1 副本）
kafka-topics.sh --create --topic orders \
  --bootstrap-server localhost:9092 \
  --partitions 3 --replication-factor 1

# 查看 Topic
kafka-topics.sh --list --bootstrap-server localhost:9092

# 控制台生产者
kafka-console-producer.sh --topic orders --bootstrap-server localhost:9092
# > hello kafka
# > 订单创建成功

# 控制台消费者（从最早开始消费）
kafka-console-consumer.sh --topic orders \
  --bootstrap-server localhost:9092 --from-beginning

# 查看消费组消费进度（Offset）
kafka-consumer-groups.sh --describe --group order-group \
  --bootstrap-server localhost:9092
# 输出含：TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET LAG
# LAG = 落后条数（消息堆积指标！）
```

## 27.4 Spring Boot 整合

### 27.4.1 依赖与配置

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer   # JSON 序列化
      acks: all                    # 见 27.5 可靠性
      retries: 3
      compression-type: lz4       # 压缩
    consumer:
      group-id: order-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest   # 无 Offset 时从最早开始（latest 则只收新消息）
      enable-auto-commit: false     # 手动提交（见 27.6）
      properties:
        spring.json.trusted.packages: "*"   # 反序列化信任包
```

### 27.4.2 消息实体

```java
@Data
public class OrderMessage {
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String status;
}
```

### 27.4.3 生产者（发送消息）

```java
@Service
public class OrderProducer {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // 订单创建后发送消息
    public void sendOrder(OrderMessage msg) {
        // key 传 orderId：相同订单进同一分区，保证该订单消息有序
        kafkaTemplate.send("orders", msg.getOrderId().toString(), msg);
        System.out.println("已发送订单消息：" + msg.getOrderId());
    }
}
```

**发送三种模式**：

```java
// 1. 发后不管（最快，可能丢）
kafkaTemplate.send("orders", msg);

// 2. 同步等待结果（可感知失败）
SendResult<String, Object> result = kafkaTemplate.send("orders", msg).get();
System.out.println("发送成功，分区：" + result.getRecordMetadata().partition());

// 3. 异步回调（推荐：兼顾性能与可感知）
kafkaTemplate.send("orders", msg).whenComplete((result, ex) -> {
    if (ex != null) {
        log.error("发送失败：{}", ex.getMessage());   // 落库重试/死信
    } else {
        log.info("发送成功：分区{} offset{}",
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }
});
```

### 27.4.4 消费者（接收消息）

```java
@Component
public class OrderConsumer {

    // 监听 topic：orders，消费组内并行
    @KafkaListener(topics = "orders", groupId = "order-group")
    public void onOrder(OrderMessage msg) {
        System.out.println("收到订单消息：" + msg);
        // 业务处理：短信通知、扣减库存、积分...
    }

    // 指定并发消费线程数（最多等于分区数）
    @KafkaListener(topics = "orders", groupId = "order-group", concurrency = "3")
    public void onOrderParallel(OrderMessage msg) { ... }

    // 按 key 过滤消费
    @KafkaListener(topics = "orders", groupId = "order-group",
            filter = "orderFilter")
    public void onFiltered(OrderMessage msg) { ... }
}
```

**消费幂等**（防止重复消费）：

```java
@Component
public class OrderConsumer {

    // 用 Redis 记录已处理的消息 ID（呼应第十二章）
    @Autowired
    private StringRedisTemplate redisTemplate;

    @KafkaListener(topics = "orders", groupId = "order-group")
    public void onOrder(OrderMessage msg) {
        String key = "kafka:processed:" + msg.getOrderId();
        // SETNX：已存在说明处理过，直接跳过
        Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", 1, TimeUnit.DAYS);
        if (Boolean.FALSE.equals(first)) {
            System.out.println("重复消息，跳过：" + msg.getOrderId());
            return;
        }
        // 真正的业务处理
        System.out.println("处理订单：" + msg.getOrderId());
    }
}
```

> **为什么要幂等**：消费端可能"处理成功但提交 Offset 失败"导致重复消费。Kafka 是**至少一次**语义，业务必须幂等。

## 27.5 分区与副本机制

### 27.5.1 副本与 ISR

```
Topic: orders, 3 分区, 2 副本（replication-factor=2）
┌─────────────┬─────────────┬─────────────┐
│ P0 Leader   │ P1 Leader   │ P2 Leader   │   Broker1
├─────────────┼─────────────┼─────────────┤
│ P0 Follower │ P1 Follower │ P2 Follower │   Broker2
└─────────────┴─────────────┴─────────────┘
```

- **Leader**：负责读写（所有读写都走 Leader）
- **Follower**：只同步数据（拉取 Leader 的日志），不对外服务
- **ISR（In-Sync Replica）**：与 Leader 保持同步的副本集合
- Follower 同步落后超过阈值 → 被踢出 ISR；Leader 挂了 → **从 ISR 中选新 Leader**

### 27.5.2 生产者 ACK 级别

| acks | 含义 | 可靠性 | 性能 |
| --- | --- | --- | --- |
| `acks=0` | 发完就算成功，不等确认 | 可能丢 | 最快 |
| `acks=1` | Leader 写入成功即返回 | 少丢 | 快 |
| `acks=all` | **ISR 全部同步成功才返回** | 最可靠 | 较慢 |

> 生产推荐：**`acks=all` + `min.insync.replicas=2` + 副本数≥3**（保证至少 2 个副本同步，Leader 挂了一个还在）。

### 27.5.3 分区分配策略（消费者组）

| 策略 | 说明 |
| --- | --- |
| RangeAssignor（默认旧） | 按 Topic 连续分片，可能不均匀 |
| **RoundRobinAssignor** | 轮询分配，尽量均匀 |
| StickyAssignor（默认新） | 粘性：平衡 + 减少重平衡扰动 |

**Rebalance（重平衡）**：消费者加入/退出/分区数变化时，分区重新分配。**Rebalance 期间整个消费者组暂停消费**，是生产事故高发点：
- 消费处理太慢触发 `max.poll.interval.ms` 超时 → 被踢 → 重平衡
- 解决方案：提高超时、`max.poll.records` 调小、处理完再 poll

## 27.6 可靠性投递（生产级重点）

### 27.6.1 端到端不丢消息

```
生产者 ──(acks=all)──► Broker ──(手动提交)──► 消费者
 ①                         ②                     ③
① 生产者：acks=all + 重试 + 幂等(enable.idempotence=true)
② Broker：副本同步（min.insync.replicas）+ 持久化
③ 消费者：手动提交 Offset（先处理业务，成功才提交）
```

### 27.6.2 手动提交 Offset（消费者）

```java
@KafkaListener(topics = "orders", groupId = "order-group")
public void onOrder(OrderMessage msg, Acknowledgment ack) {
    try {
        // 1. 先处理业务（发送短信/更新数据库）
        doBusiness(msg);

        // 2. 业务成功后才提交 Offset
        ack.acknowledge();
    } catch (Exception e) {
        // 3. 业务失败不提交 → 下次拉取会重新消费（至少一次）
        log.error("处理失败，等待重试", e);
        // 可重试 N 次后进死信 Topic（见 27.7）
    }
}
```

### 27.6.3 生产者幂等与事务

```properties
# 幂等生产者：自动去重（同一批次内消息不重复）
enable.idempotence=true

# 事务消息（跨分区原子性，一般用不到）
transactional.id=order-tx-1
```

## 27.7 消息堆积与顺序性

### 27.7.1 消息堆积（Lag 飙高）

**排查**：

```bash
# 查看消费组 Lag
kafka-consumer-groups.sh --describe --group order-group --bootstrap-server localhost:9092
#  LAG 很大 → 消费速度跟不上生产速度
```

**原因与对策**：

| 原因 | 对策 |
| --- | --- |
| 分区数 < 消费者数 | 增加分区数（先加分区，再加消费者） |
| 单个消费处理太慢（DB 慢/接口慢） | 优化消费逻辑；异步化处理 |
| 消费者被重平衡踢出 | 调大 `max.poll.interval.ms`，减小 `max.poll.records` |
| 消费逻辑有异常一直重试 | 加死信机制，隔离坏消息 |
| 下游能力不足 | 消费后转投递给工作线程池（注意有序场景） |

### 27.7.2 消息顺序性

Kafka 只能保证**分区内有序**。三种方案：

```java
// 方案一：同 key 同分区（推荐）
kafkaTemplate.send("orders", orderId.toString(), msg);
// 同一 orderId 的所有消息哈希到同一分区 → 该订单消息有序

// 方案二：单分区（牺牲并行）
// 方案三：消费端本地队列 + 单线程（按 key 分组处理）
```

> 面试回答模板：`Topic` 拆分区 → 同一业务 key（如订单号）哈希路由到同一分区 → 分区内追加有序 → 消费端单线程处理该分区（或按 key 分组串行）。

## 27.8 实战：延迟队列与死信

### 27.8.1 延迟消息（Kafka 无原生延迟，用轮询 Topic 模拟）

```java
// 延迟队列：多级延迟 Topic + 定时扫描
@Scheduled(fixedDelay = 1000)
public void checkDelay() {
    // 读取"延迟一分钟"的 Topic，到期消息转发到正式 Topic
    List<OrderMessage> expired = delayTopicConsumer.pollExpired();
    expired.forEach(msg -> kafkaTemplate.send("orders", msg));
}
```

> 与 16.6 RabbitMQ 延迟队列对比：RabbitMQ 用死信/延迟插件，Kafka 靠应用层实现。生产也可以用 Redis 的 `ZSET` 做延迟队列（评分：`score = 到期时间戳`）。

### 27.8.2 死信 Topic

```java
@Component
public class RetryConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Map<String, Integer> retryCount = new ConcurrentHashMap<>();  // 记录重试次数

    @KafkaListener(topics = "orders", groupId = "order-group")
    public void onOrder(OrderMessage msg, Acknowledgment ack) {
        try {
            doBusiness(msg);
            ack.acknowledge();
            retryCount.remove(key(msg));
        } catch (Exception e) {
            int count = retryCount.merge(key(msg), 1, Integer::sum);
            if (count >= 3) {
                // 重试 3 次还失败 → 进死信 Topic，人工排查
                kafkaTemplate.send("orders-dlq", msg);
                retryCount.remove(key(msg));
                ack.acknowledge();   // 死信也确认，避免死循环
                log.error("进入死信：{}", msg);
            } else {
                // 下次轮询自动重试（不提交 Offset）
                log.warn("第 {} 次处理失败，待重试", count);
            }
        }
    }

    private String key(OrderMessage msg) { return msg.getOrderId().toString(); }
}
```

### 27.8.3 与大数据生态结合

```
业务系统 ──► Kafka ──► 日志采集（Filebeat/Logstash）──► Elasticsearch/Kibana
          ──► 流处理（Flink/Spark Streaming）
          ──► 数据仓库（Hadoop/Hive）
          ──► 削峰填谷（秒杀：请求先写 Kafka，后端慢慢消化）
```

**秒杀削峰案例**（呼应 12 章缓存）：

```
用户点击秒杀 ──► Redis 预扣库存（快速拦截）
                │
                ▼
        写入 Kafka 订单队列 ──► 后端消费者异步下单（慢慢消化洪峰）
                │
        返回"排队中，请稍后查看结果" ← 用户体验友好
```

## 27.9 小结与练习

**本章重点**：
- Kafka vs RabbitMQ 选型：吞吐优先、日志/流处理用 Kafka
- 为什么快：顺序写 + PageCache + 零拷贝 + 批量压缩 + 分区并行
- 核心概念：Broker / Topic / Partition / Offset / 副本 / 消费者组
- 消费者组：组内分摊分区（并行）、组间广播；分区内有序
- 副本：Leader 读写 + ISR 同步 + acks 级别（0/1/all）
- 可靠性：`acks=all` + 手动提交 Offset + 幂等消费（Redis SETNX）
- 消息堆积：加分区加消费者、调 poll 参数、死信隔离
- 顺序性：同 key 同分区 + 分区内单线程消费
- 与大数据/秒杀削峰结合

**面试题参考**：
1. Kafka 和 RabbitMQ 怎么选？
2. Kafka 为什么吞吐量这么高？
3. 讲一下 Kafka 的架构（Broker/Topic/Partition/消费者组）？
4. 消费者组的作用？一个消费者可以消费多个分区吗？
5. 如何保证消息不丢失？（生产者/服务端/消费者三端）
6. 如何保证消息的顺序性？
7. 消息堆积怎么办？怎么排查？
8. Kafka 的副本机制？ISR 是什么？Leader 挂了怎么选？
9. 重复消费怎么解决？（幂等）
10. 为什么说 Kafka 是"至少一次"语义？

**课后练习**：
1. Docker 启动 Kafka，创建 Topic，用命令行生产者/消费者收发消息。
2. Spring Boot 整合 Kafka，发送订单消息，消费者打印接收。
3. 验证分区与消费者组：3 个分区 + 3 个消费者观察并行消费；改成 2 个消费者观察分区分摊。
4. 模拟重复消费：业务抛异常不提交 Offset，重启消费者观察消息重新消费。
5. 给订单消息按 orderId 做 key，验证同一订单消息始终进同一分区。

上一章：[26-Netty.md](./26-Netty.md) | 下一章：[28-设计原则深度.md](./28-设计原则深度.md) | 返回目录：[README.md](./README.md)
