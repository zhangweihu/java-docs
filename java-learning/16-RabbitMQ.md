# 第十六章 RabbitMQ 消息队列

> 本章目标：理解消息队列三大作用（异步/解耦/削峰），掌握 RabbitMQ 核心概念与五种工作模式，会用 Spring Boot 完成生产消费、延迟队列、可靠性投递等企业级场景。
>
> 前置知识：第十五章 Spring Cloud（本章与微服务是黄金搭档）。

## 16.1 为什么需要消息队列

### 16.1.1 场景一：异步

用户下单后，需要：写订单（50ms）+ 发短信（300ms）+ 发邮件（300ms）+ 积分（100ms）。同步执行总耗时 **750ms**，用户等得很痛苦。

用消息队列改造后：

```
同步：请求 ──► 写订单 ──► 发短信 ──► 发邮件 ──► 积分      共 750ms
异步：请求 ──► 写订单 ──► 发消息给 MQ ──► 立即返回          共 60ms
                    （短信/邮件/积分服务从 MQ 慢慢消费）
```

### 16.1.2 场景二：解耦

订单系统直接调用短信、邮件系统，如果哪天要新增"微信通知"，就得改订单系统代码。引入 MQ 后，订单系统只管发消息，**谁消费、新增谁**都不影响订单系统。

### 16.1.3 场景三：削峰

双十一瞬间 10 万请求直接打到数据库，数据库必然崩溃。MQ 像"泄洪闸"：先把请求全部接住放队列里，后端服务**按自己的处理能力**慢慢消费，保证系统不被打垮。

### 16.1.4 主流消息队列对比

| 对比项 | RabbitMQ | Kafka | RocketMQ |
| --- | --- | --- | --- |
| 定位 | 通用消息中间件，功能最全 | 大数据/日志流式处理 | 阿里电商场景 |
| 吞吐量 | 万级/秒 | **百万级/秒** | 十万级/秒 |
| 消息可靠性 | 高（确认机制完善） | 高（副本机制） | 高 |
| 学习成本 | 低（文档好、生态成熟） | 中 | 中 |
| 典型场景 | **业务解耦、定时任务、延迟消息** | 日志采集、实时计算 | 削峰填谷 |

> 结论：**业务系统**（订单、通知、异步任务）首选 RabbitMQ；**大数据**场景用 Kafka。

## 16.2 RabbitMQ 核心概念

### 16.2.1 五大核心角色

```
                    ┌─────────────────────────────┐
                    │        RabbitMQ 服务器        │
                    │                             │
 Producer ──发消息──►│  Exchange（交换机）          │
  生产者              │      │ routingKey 路由      │
                    │      ▼                     │
                    │  Binding（绑定关系）         │
                    │      │                     │
                    │      ▼                     │
                    │   Queue（队列）◄──存消息      │
                    │      │                     │
                    └──────┼─────────────────────┘
                           │ 推送/拉取
                           ▼
                      Consumer（消费者）
```

| 概念 | 说明 |
| --- | --- |
| **Producer 生产者** | 发送消息的一方 |
| **Consumer 消费者** | 接收消息的一方 |
| **Queue 队列** | 消息的存储容器，先进先出（FIFO） |
| **Exchange 交换机** | 消息的"路由器"：决定把消息投递到哪些队列 |
| **Binding 绑定** | 交换机与队列之间的绑定关系 + **RoutingKey 路由键** |
| **VHost 虚拟主机** | 逻辑隔离空间，不同项目用不同 VHost |

### 16.2.2 交换机类型

| 类型 | 路由规则 | 类比 |
| --- | --- | --- |
| **Direct 直连** | RoutingKey **完全匹配** | 精准投递 |
| **Fanout 广播** | 忽略 RoutingKey，发给**所有绑定队列** | 群发 |
| **Topic 主题** | RoutingKey **通配符匹配**（`*` 一个词、`#` 多个词） | 订阅分组 |
| Headers 头匹配 | 按消息头匹配（已少用） | — |

### 16.2.3 消息的确认与持久化

**面试必问：如何保证消息不丢？**（三端确认）

| 环节 | 方案 |
| --- | --- |
| 生产者 → MQ | **Confirm 确认模式**：MQ 收到消息后回执 |
| MQ 自身 | **持久化**：交换机、队列、消息都设为 durable |
| MQ → 消费者 | **手动 ack**：消费成功才 ack，失败则重回队列 |

## 16.3 安装 RabbitMQ（Docker）

```bash
# RabbitMQ 3.13 自带 web 管理插件，无需额外安装
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3.13-management

# 访问管理控制台：http://localhost:15672   （默认账号密码均为 guest）
# 端口说明：5672 = 客户端连接端口，15672 = Web 管理界面端口
```

## 16.4 五种工作模式

### 16.4.1 模式一：简单模式（Hello World）

一个生产者 → 一个队列 → 一个消费者。入门必写。

```java
// 生产者
ConnectionFactory factory = new ConnectionFactory();
factory.setHost("localhost");
try (Connection conn = factory.newConnection();
     Channel channel = conn.createChannel()) {
    // 声明队列：durable=true 持久化，重启不丢
    channel.queueDeclare("hello", true, false, false, null);
    channel.basicPublish("", "hello", null, "你好 RabbitMQ".getBytes());
    System.out.println("消息已发送");
}

// 消费者
ConnectionFactory factory = new ConnectionFactory();
factory.setHost("localhost");
try (Connection conn = factory.newConnection();
     Channel channel = conn.createChannel()) {
    channel.queueDeclare("hello", true, false, false, null);
    // 回调式消费：消息到达自动执行
    channel.basicConsume("hello", true,
        (consumerTag, delivery) ->
            System.out.println("收到：" + new String(delivery.getBody())),
        consumerTag -> {});
    Thread.sleep(5000);   // 等消息消费完
}
```

> 简单模式直接用默认交换机（空字符串），RoutingKey 就是队列名。实际项目几乎不直接用原生 API，而是用 Spring Boot 封装（见 16.5）。

### 16.4.2 模式二：Work 工作队列（任务分发）

一个队列，**多个消费者**，消息被自动分发（默认**轮询**，一人一条）。

```java
// 两个消费者同时监听同一个队列，默认轮询分发
// 性能优化：一次只取一条、消费完再取下一条（能者多劳）
channel.basicQos(1);   // 每次只预取 1 条
channel.basicConsume("task_queue", false, deliverCallback, cancelCallback);
// 处理完业务后手动 ack：channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
```

**应用场景**：短信群发、邮件群发、图片处理——多个 worker 并行消费。

### 16.4.3 模式三：发布订阅（Fanout 广播）

一条消息发给**所有**绑定的队列。交换机类型 `fanout`。

```java
// 生产者
channel.exchangeDeclare("logs", BuiltinExchangeType.FANOUT);
channel.basicPublish("logs", "", null, "广播消息".getBytes());

// 每个消费者：创建自己的临时队列（随机名），绑定到 logs 交换机
String queueName = channel.queueDeclare().getQueue();   // 临时队列，断开即销毁
channel.queueBind(queueName, "logs", "");
```

**应用场景**：系统公告、站内信群发、订单创建后所有关心方都收到通知。

### 16.4.4 模式四：路由（Direct）

交换机类型 `direct`，RoutingKey **精确匹配**才投递。

```java
// 生产者：error 级别日志发到 error 队列，info/warning 发到 log 队列
channel.exchangeDeclare("direct_logs", BuiltinExchangeType.DIRECT);
channel.basicPublish("direct_logs", "error", null, "错误日志".getBytes());
channel.basicPublish("direct_logs", "info", null, "普通日志".getBytes());

// 消费者：只关心 error
channel.queueBind(queueName, "direct_logs", "error");
```

**应用场景**：日志分级收集、按消息类型分发。

### 16.4.5 模式五：主题（Topic）

交换机类型 `topic`，RoutingKey **通配符匹配**，最灵活：

| 通配符 | 含义 |
| --- | --- |
| `*` | 匹配**一个**单词 |
| `#` | 匹配**零个或多个**单词 |

```java
// RoutingKey 用"点"分词，例如：order.created、order.paid、user.register
channel.exchangeDeclare("topic_logs", BuiltinExchangeType.TOPIC);

// 生产者发订单相关消息
channel.basicPublish("topic_logs", "order.created", null, "订单创建".getBytes());
channel.basicPublish("topic_logs", "order.paid", null, "订单支付".getBytes());

// 消费者 A 订阅所有 order 开头的消息
channel.queueBind(queueA, "topic_logs", "order.*");
// 消费者 B 订阅所有与订单支付、库存相关的消息
channel.queueBind(queueB, "topic_logs", "*.paid");
channel.queueBind(queueB, "topic_logs", "order.#");
```

**应用场景**：按业务域订阅（订单域、用户域、支付域），微服务按需消费。

### 16.4.6 五种模式速记表

| 模式 | 交换机 | RoutingKey | 场景 |
| --- | --- | --- | --- |
| 简单 | 默认（空） | 队列名 | 入门 |
| Work | 默认（空） | 队列名 | 任务分发、并行消费 |
| 发布订阅 | fanout | 忽略 | 广播、群发 |
| 路由 | direct | 精确匹配 | 按类型分发 |
| 主题 | topic | 通配符 | 按业务域订阅 |

## 16.5 Spring Boot 整合（重点）

### 16.5.1 依赖与配置

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    publisher-confirm-type: correlated   # 开启生产者 Confirm 确认
    publisher-returns: true              # 消息路由失败时回调
```

### 16.5.2 声明交换机、队列、绑定（配置类）

```java
package com.example.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用配置类声明交换机、队列、绑定关系（等价于 RabbitMQ 控制台手动创建）
 */
@Configuration
public class RabbitConfig {

    // ============ Topic 示例：订单消息 ============
    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_CREATE_QUEUE = "order.create.queue";
    public static final String ORDER_PAID_QUEUE = "order.paid.queue";

    // 1. 声明交换机（topic 类型，durable 持久化）
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    // 2. 声明队列
    @Bean
    public Queue orderCreateQueue() {
        return new Queue(ORDER_CREATE_QUEUE, true);
    }

    @Bean
    public Queue orderPaidQueue() {
        return new Queue(ORDER_PAID_QUEUE, true);
    }

    // 3. 绑定关系
    @Bean
    public Binding bindingCreate(Queue orderCreateQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderCreateQueue).to(orderExchange).with("order.created");
    }

    @Bean
    public Binding bindingPaid(Queue orderPaidQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderPaidQueue).to(orderExchange).with("order.paid");
    }
}
```

### 16.5.3 生产者发送消息

```java
package com.example.mq.controller;

import com.example.mq.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/order")
public class OrderController {

    private final RabbitTemplate rabbitTemplate;

    public OrderController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/create")
    public String createOrder(@RequestParam String orderId) {
        // 构造消息体
        String message = "订单创建：" + orderId + "，traceId=" + UUID.randomUUID();
        // 发送：交换机、路由键、消息内容
        rabbitTemplate.convertAndSend(RabbitConfig.ORDER_EXCHANGE, "order.created", message);
        log.info("已发送订单创建消息：{}", message);
        return "下单成功，异步处理中...";
    }
}
```

### 16.5.4 消费者监听消息

```java
package com.example.mq.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderConsumer {

    /** 监听 order.create.queue 队列：下单后异步发送短信 */
    @RabbitListener(queues = "order.create.queue")
    public void handleOrderCreate(String message) {
        log.info("[短信服务] 收到订单创建消息，开始发短信：{}", message);
        try {
            Thread.sleep(200);   // 模拟发短信耗时
            log.info("[短信服务] 短信发送成功");
        } catch (Exception e) {
            log.error("短信发送失败", e);
            // 业务失败时抛出异常 → 消息重回队列重试
            throw new RuntimeException(e);
        }
    }

    /** 监听 order.paid.queue 队列：支付成功后异步开发票 */
    @RabbitListener(queues = "order.paid.queue")
    public void handleOrderPaid(String message) {
        log.info("[发票服务] 收到支付成功消息，开始开发票：{}", message);
    }
}
```

启动项目，`POST /order/create?orderId=1001`，控制台即可看到消费者异步处理日志。

### 16.5.5 消息转换器（对象消息）

默认 `SimpleMessageConverter` 只能传字符串和字节。传对象时换成 **Jackson 序列化**：

```java
@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();   // 对象自动转 JSON
    }
}
```

```java
// 生产者：直接发对象
Order order = new Order(1001L, "华为手机", 5999.0);
rabbitTemplate.convertAndSend(ORDER_EXCHANGE, "order.created", order);

// 消费者：接收对象
@RabbitListener(queues = "order.create.queue")
public void handleOrderCreate(Order order) {
    log.info("收到订单对象：{}", order.getProductName());
}
```

## 16.6 死信队列与延迟队列（企业必考）

### 16.6.1 死信（Dead Letter）

消息成为**死信**的三种情况：

1. 消费者**拒收**且不重回队列（`basicNack` + requeue=false）
2. 消息**过期**（TTL 超时没人消费）
3. 队列**已满**

死信不会消失，而是被投递到**死信交换机**（DLX），由专门队列接收处理。

### 16.6.2 经典场景：订单超时自动关闭

下单后 30 分钟未支付，自动关闭订单。做法：

```
生产者 ──► 业务交换机 ──► 延迟队列（消息 TTL=30分钟，无消费者）
                                  │ 消息过期 → 成为死信
                                  ▼
                          死信交换机 ──► 关闭订单队列 ──► 消费者：查订单状态并关闭
```

```java
@Configuration
public class DelayOrderConfig {

    // 1. 业务交换机（订单消息正常入口）
    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange("order.delay.exchange", true, false);
    }

    // 2. 延迟队列：TTL 30 分钟，无人消费；过期后进入死信交换机
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable("order.delay.queue")
                .ttl(30 * 60 * 1000)                            // 消息存活 30 分钟
                .deadLetterExchange("order.dlx.exchange")       // 死信交换机
                .deadLetterRoutingKey("order.close")            // 死信路由键
                .build();
    }

    // 3. 死信交换机与关闭订单队列
    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange("order.dlx.exchange", true, false);
    }

    @Bean
    public Queue orderCloseQueue() {
        return new Queue("order.close.queue", true);
    }

    @Bean
    public Binding bindingDelay(Queue orderDelayQueue, DirectExchange orderDelayExchange) {
        return BindingBuilder.bind(orderDelayQueue).to(orderDelayExchange).with("order.create");
    }

    @Bean
    public Binding bindingClose(Queue orderCloseQueue, DirectExchange orderDlxExchange) {
        return BindingBuilder.bind(orderCloseQueue).to(orderDlxExchange).with("order.close");
    }
}
```

```java
// 消费者：收到死信 = 订单已超时，执行关闭
@RabbitListener(queues = "order.close.queue")
public void closeTimeoutOrder(String orderInfo) {
    log.info("[订单关闭] 订单超时未支付，开始关闭：{}", orderInfo);
    // 查数据库订单状态，若仍为"待支付"则置为"已关闭"
}
```

> **注意**：`order.delay.queue` **不能有消费者**，否则消息被消费掉就不会过期进入死信了。

### 16.6.3 其他延迟方案对比

| 方案 | 原理 | 适用 |
| --- | --- | --- |
| **死信队列 + TTL**（本文） | 消息过期转死信 | 最简单，但 TTL 粒度受限 |
| **延迟消息插件** `rabbitmq_delayed_message_exchange` | 交换机原生支持延迟 | 灵活，社区插件 |
| **Redis 过期键 + 定时轮询** | ZSet 按时间排序轮询 | 轻量场景 |

## 16.7 可靠性投递（消息不丢）

### 16.7.1 生产者确认 Confirm

```java
@Configuration
public class RabbitConfig {

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // 1. Confirm 回调：消息到达交换机成功/失败
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.info("消息已到达交换机：{}", correlationData);
            } else {
                log.error("消息投递失败：{}", cause);
                // 落库失败表，定时任务重发
            }
        });
        // 2. Returns 回调：消息未路由到任何队列
        template.setReturnsCallback(returned -> {
            log.error("消息路由失败，退回：{}", new String(returned.getMessage().getBody()));
        });
        template.setMandatory(true);
        return template;
    }
}
```

### 16.7.2 消费者手动 ack（重要）

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual    # 改为手动确认
```

```java
@Slf4j
@Component
public class SafeConsumer {

    /**
     * @param deliveryTag 消息唯一标记
     * @param channel     用于 ack/nack 的通道
     */
    @RabbitListener(queues = "order.create.queue")
    public void handle(Order order, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       Channel channel) throws IOException {
        try {
            // 1. 处理业务
            log.info("处理订单：{}", order);
            Thread.sleep(100);

            // 2. 成功 → 确认，删除队列中的消息
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理失败", e);
            // 3. 失败 → 重回队列尾部，等待重试（requeue=true）
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
```

> **面试常问：手动 ack 失败消息会无限重试，怎么办？**
> 方案：① 设置重试次数上限（`spring.rabbitmq.listener.simple.retry.max-attempts`）② 超过次数进入死信队列人工处理 ③ 记录失败表 + 定时补偿。

## 16.8 小结与练习

**本章重点**：
- MQ 三大作用：**异步、解耦、削峰**
- 核心概念：交换机（direct/fanout/topic）、队列、RoutingKey
- 消息不丢三端保障：**Confirm + 持久化 + 手动 ack**
- 延迟队列 = 死信队列 + TTL（订单超时经典场景）
- 消息失败处理：手动 ack + 重试 + 死信兜底

**面试题参考**：
1. 为什么用消息队列？有什么缺点？（增加复杂度、消息丢失、重复消费）
2. 如何保证消息不丢失？
3. 如何保证消息不被重复消费？（幂等性：数据库唯一键 / Redis setnx）
4. 如何保证消息顺序？（一个队列绑定一个消费者 / 分区键）
5. 死信是什么？怎么实现延迟队列？
6. 消息积压怎么处理？（临时扩容消费者 / 紧急消费到新队列）

**课后练习**：
1. 用 Docker 启动 RabbitMQ，控制台手动创建一个 topic 交换机 + 队列 + 绑定。
2. 用 Spring Boot 实现：下单 → 发送订单创建消息 → 短信服务消费（打印日志）。
3. 实现订单 30 分钟未支付自动关闭（死信队列 + TTL），用 10 秒 TTL 快速验证。
4. 给生产者开启 Confirm，消费者改为手动 ack，模拟消费失败观察消息重回队列。
5. 思考：短信接口返回"重复发送"如何用 Redis 做幂等？

上一章：[15-SpringCloud.md](./15-SpringCloud.md) | 下一章：[17-MyBatisPlus.md](./17-MyBatisPlus.md) | 返回目录：[README.md](./README.md)
