# 第十五章 Spring Cloud 微服务

> 本章目标：理解微服务架构思想，掌握服务注册与发现（Nacos）、远程调用（OpenFeign）、网关（Gateway）、配置中心、熔断降级等核心组件，会用 Spring Cloud 搭建一个可运行的微服务示例。
>
> 前置知识：第九章 Spring Boot、第十二章 Redis、第十三章 Spring Security。

## 15.1 为什么需要微服务

### 15.1.1 单体架构的痛点

回顾第九章我们构建的用户管理系统，它是一个典型的**单体应用**：一个工程、一个数据库、一次部署。

随着业务增长，单体应用会遇到以下问题：

| 问题 | 描述 |
| --- | --- |
| 代码臃肿 | 所有模块（用户、订单、支付）在一个工程里，改一行代码要重新部署整个系统 |
| 团队协作难 | 多人改同一个工程，合并冲突频繁，交付互相阻塞 |
| 扩展不灵活 | 只有订单模块压力大，却要扩容整个应用（浪费资源） |
| 技术栈绑定 | 全系统只能用一种语言、一种数据库，无法按模块选型 |
| 故障蔓延 | 一个模块 OOM，整个系统崩溃 |

### 15.1.2 微服务架构

**微服务（Microservices）**：把一个大型单体应用拆分成**多个独立的小服务**，每个服务：

- 独立开发、独立部署、独立扩展
- 拥有自己的数据库（**数据库隔离**）
- 通过 **HTTP/RPC** 相互通信
- 可用不同的技术栈（Java、Go、Node 各管各的）

```
                    ┌────────────────────────────┐
                    │        网关 Gateway        │  统一入口：路由、鉴权、限流
                    └─────────────┬──────────────┘
             ┌────────────────────┼────────────────────┐
             ▼                    ▼                    ▼
      ┌────────────┐      ┌────────────┐      ┌────────────┐
      │ 用户服务    │      │ 订单服务    │      │ 支付服务    │
      │ (8081)     │      │ (8082)     │      │ (8083)     │
      └────────────┘      └────────────┘      └────────────┘
             │                    │                    │
        ┌────┴─────┐        ┌────┴─────┐        ┌────┴─────┐
        │ 用户库    │        │ 订单库    │        │ 支付库    │
        └──────────┘        └──────────┘        └──────────┘
             └───────────────┬───────────────────────┘
                             ▼
                 ┌──────────────────────┐
                 │ Nacos 注册中心/配置中心 │  服务发现 + 统一配置
                 └──────────────────────┘
```

### 15.1.3 微服务的挑战

拆分后带来了新问题，这正是 Spring Cloud 各组件要解决的：

| 挑战 | 解决方案（组件） |
| --- | --- |
| 服务地址动态变化，怎么找到对方？ | **注册中心**：Nacos / Eureka / Consul |
| 调用方怎么负载均衡？ | **负载均衡**：Spring Cloud LoadBalancer / Ribbon |
| 服务间如何优雅调用？ | **声明式调用**：OpenFeign |
| 统一入口、鉴权、限流？ | **网关**：Spring Cloud Gateway |
| 配置散落各处，改配置要重启？ | **配置中心**：Nacos Config |
| 服务挂了怎么避免雪崩？ | **熔断降级**：Sentinel / Resilience4j |
| 一次请求跨多个服务，如何排查？ | **链路追踪**：Micrometer Tracing + Zipkin |

> 概念速记：微服务 = 服务注册发现 + 远程调用 + 网关 + 配置中心 + 熔断 + 追踪。

## 15.2 技术选型与版本说明

国内企业最主流的是 **Spring Cloud Alibaba** 全家桶（Nacos + Sentinel），本教程采用：

| 组件 | 选型 | 说明 |
| --- | --- | --- |
| 注册中心 + 配置中心 | **Nacos** | 阿里巴巴开源，国内事实标准 |
| 服务调用 | **OpenFeign** + LoadBalancer | 声明式 HTTP 客户端 |
| 网关 | **Spring Cloud Gateway** | 基于 WebFlux 响应式 |
| 熔断限流 | **Sentinel** | 阿里巴巴开源 |
| 链路追踪 | Micrometer Tracing | Spring Boot 3 官方方案 |

**版本搭配（重要，Spring Cloud 版本号很特殊）**：

| Spring Boot | Spring Cloud | Spring Cloud Alibaba |
| --- | --- | --- |
| 2.7.x | 2021.0.x（代号 Jubilee） | 2021.0.5.0 |
| 3.2.x | 2023.0.x（代号 Leyton） | 2023.0.1.0 |

> Spring Cloud 版本用"年号"命名（如 2023.0.x），不要和 Spring Boot 的 2.x/3.x 混淆。
> 后面示例基于 **Spring Boot 3.2 + Spring Cloud 2023.0.x + Spring Cloud Alibaba 2023.0.1.0**，JDK 17。

## 15.3 注册中心 Nacos

### 15.3.1 Nacos 是什么

**Nacos（Naming and Configuration Service）**：一个集**服务注册发现**与**动态配置**于一体的平台。

核心能力：
- **服务注册**：服务启动时把自己的 IP:端口 上报给 Nacos
- **服务发现**：调用方从 Nacos 获取目标服务的实例列表
- **心跳检测**：服务每 5 秒发一次心跳，30 秒没心跳则摘除实例
- **配置中心**：集中管理配置，修改后动态下发、无需重启

### 15.3.2 安装 Nacos（Docker）

```bash
# 1. 拉取镜像并启动单机版 Nacos（默认端口 8848）
docker run -d --name nacos \
  -p 8848:8848 -p 9848:9848 -p 9849:9849 \
  -e MODE=standalone \
  -e NACOS_AUTH_ENABLE=false \
  nacos/nacos-server:v2.3.2

# 2. 启动后访问控制台
# http://localhost:8848/nacos   （默认账号密码均为 nacos）
```

> 如果本机没有 Docker，也可以去 Nacos 官网下载压缩包，Windows 直接双击 `bin/startup.cmd -m standalone`。

### 15.3.3 编写一个服务并注册

**步骤 1：创建 Maven 工程 `cloud-demo`**（父工程，管理依赖版本）

```xml
<!-- pom.xml（父工程） -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>

<properties>
    <java.version>17</java.version>
    <spring-cloud.version>2023.0.1</spring-cloud.version>
    <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**步骤 2：创建子模块 `user-service`（用户服务，端口 8081）**

```xml
<!-- user-service/pom.xml 关键依赖 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

```yaml
# user-service/src/main/resources/application.yml
server:
  port: 8081

spring:
  application:
    name: user-service          # 服务名：其他服务通过它来调用
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848   # Nacos 地址
```

```java
package com.example.user;

@SpringBootApplication
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

@RestController
@RequestMapping("/user")
public class UserController {

    // 返回当前服务实例信息，便于观察负载均衡
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "user-service",
                "host", InetAddress.getLocalHost().getHostAddress(),
                "port", 8081
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable Long id) {
        return Map.of("id", id, "name", "用户" + id);
    }
}
```

启动后，打开 Nacos 控制台 → 服务管理 → 服务列表，就能看到 `user-service` 已注册。

> 可以再复制一份 `order-service`（端口 8082，同理注册），用来演示**多实例**与**互相调用**。

### 15.3.4 服务发现原理

```
user-service 启动 ──► 向 Nacos 上报 (serviceName=user-service, ip, port)
                              │ 存储到注册表，并建立心跳
order-service 调用时 ──► 向 Nacos 查询 user-service 的实例列表
                              │
                              ▼
                   获得 [{ip:192.168.1.10,port:8081}, {...}]
                              │ 负载均衡策略挑选一个
                              ▼
                   RestTemplate/Feign 发起 HTTP 调用
```

**面试常问：Nacos 与 Eureka 的区别？**

| 对比项 | Nacos | Eureka |
| --- | --- | --- |
| 一致性 | AP + CP 模式可选（默认 AP） | 只支持 AP（最终一致） |
| 健康检查 | 心跳 + 主动探测（临时/持久实例） | 只靠心跳 |
| 配置中心 | 自带 | 需要配合 Config Server |
| 维护状态 | 阿里活跃维护 | 2.0 已停止维护（进入维护模式） |
| 其他 | 支持分组/命名空间/权重 | 简单 |

## 15.4 远程调用：OpenFeign

服务间调用，最简单的是 `RestTemplate`，但每次都要手写 URL、解析 JSON，繁琐且易错。**OpenFeign** 用声明式接口解决：**写一个接口 = 一次远程调用**。

### 15.4.1 在 order-service 中引入 Feign

```xml
<!-- order-service/pom.xml -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

```java
package com.example.order;

@SpringBootApplication
@EnableFeignClients        // 开启 Feign 客户端扫描
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

### 15.4.2 声明 Feign 客户端

```java
package com.example.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 声明式远程调用接口：
 * 方法签名 = 被调服务的 HTTP 接口，无需写实现，Feign 自动生成代理
 */
@FeignClient(name = "user-service")     // name = 目标服务在 Nacos 注册的名字
public interface UserFeignClient {

    @GetMapping("/user/{id}")
    Map<String, Object> getUserById(@PathVariable Long id);
}
```

### 15.4.3 使用 Feign

```java
package com.example.order.controller;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private UserFeignClient userFeignClient;

    @GetMapping("/{orderId}/user/{userId}")
    public Map<String, Object> orderWithUser(@PathVariable Long orderId,
                                             @PathVariable Long userId) {
        // 一行代码远程调用 user-service 的 /user/{id} 接口
        Map<String, Object> user = userFeignClient.getUserById(userId);

        return Map.of(
                "orderId", orderId,
                "user", user,
                "order-service", "order"
        );
    }
}
```

启动两个服务后，访问 `http://localhost:8082/order/1001/user/1`，即可看到 order-service 成功远程调用 user-service。

### 15.4.4 负载均衡

OpenFeign 默认集成 LoadBalancer，**服务有多个实例时自动轮询**。验证方法：启动**两个** user-service（端口 8081、8082，改 `--server.port`），连续调用多次，观察 `info()` 返回的端口交替变化。

```bash
# 用命令行启动第二个实例（IDEA 里也可配置多个启动项）
java -jar user-service.jar --server.port=8082
```

> **面试常问：Feign 调用超时怎么配置？**
> Feign 底层是 HTTP 客户端，可设置连接/读取超时：

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:                    # 对所有服务生效
            connect-timeout: 3000     # 连接超时 3 秒
            read-timeout: 5000        # 读取超时 5 秒
```

## 15.5 网关 Spring Cloud Gateway

### 15.5.1 为什么需要网关

如果客户端直接访问各个微服务：

- 每个服务都要写 CORS、鉴权、限流逻辑 → **重复**
- 所有服务端口暴露给前端 → **不安全**
- 前端要记 N 个地址 → **不友好**

网关作为**所有流量的统一入口**，负责：

1. **路由**：根据 URL 把请求转发到对应服务
2. **鉴权**：统一校验 JWT（衔接第十三章）
3. **限流**：防止恶意刷接口
4. **跨域**：统一处理 CORS
5. **日志**：统一记录访问日志

### 15.5.2 创建网关模块 `gateway`（端口 8080）

```xml
<!-- gateway/pom.xml 关键依赖 -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<!-- 注意：网关基于 WebFlux，不能引入 spring-boot-starter-web！ -->
```

```yaml
# gateway/src/main/resources/application.yml
server:
  port: 8080

spring:
  application:
    name: gateway
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
    gateway:
      routes:                      # 路由规则列表
        - id: user-route           # 路由唯一标识
          uri: lb://user-service   # lb:// 表示从注册中心按负载均衡找服务
          predicates:              # 断言（匹配条件）
            - Path=/user/**        # 以 /user/ 开头的请求 → user-service
        - id: order-route
          uri: lb://order-service
          predicates:
            - Path=/order/**
```

启动网关后，访问：

```
http://localhost:8080/user/1        → 转发到 user-service 的 /user/1
http://localhost:8080/order/1001/user/1   → 转发到 order-service
```

客户端只认 `8080` 一个入口，微服务内部结构完全屏蔽。

### 15.5.3 常用断言（Predicate）与过滤器（Filter）

**断言**（判断请求是否匹配路由）：

| 断言 | 示例 | 说明 |
| --- | --- | --- |
| Path | `Path=/user/**` | 路径匹配 |
| Method | `Method=GET,POST` | 请求方法 |
| Header | `Header=X-Request-Id, \d+` | 请求头匹配 |
| Query | `Query=name` | 查询参数 |
| Cookie | `Cookie=token, abc` | Cookie 匹配 |
| After/Before | `After=2026-01-01T00:00:00+08:00` | 时间区间 |

**过滤器**（在请求转发前后做处理），分为两类：

- **GlobalFilter**：所有路由生效（鉴权就写在这里）
- **GatewayFilter**：只对指定路由生效（`filters:` 配置）

**示例：全局 JWT 鉴权过滤器（衔接第十三章）**

```java
package com.example.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局过滤器：白名单放行，其余请求校验 Authorization 头
 * 注意：网关是响应式编程（WebFlux），不返回 null，而是 Mono
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    // 不需要登录就能访问的路径
    private static final String[] WHITE_LIST = {"/user/login", "/user/register"};

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. 白名单直接放行
        for (String white : WHITE_LIST) {
            if (path.startsWith(white)) {
                return chain.filter(exchange);
            }
        }

        // 2. 取 Authorization 头：Bearer xxx
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 3. 校验 JWT（用第十三章的 JwtUtil），解析出的 userId 放入请求头传给下游服务
        String userId = JwtUtil.parseUserId(token.substring(7));
        ServerHttpRequest newRequest = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .build();
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    @Override
    public int getOrder() {
        return -100;   // 数字越小优先级越高，最先执行
    }
}
```

## 15.6 配置中心 Nacos Config

### 15.6.1 解决的问题

服务越来越多，配置文件散落各处。改一个数据库密码，要改 N 个服务、重启 N 次。**配置中心**把配置集中管理，支持**动态刷新**（改配置不用重启）。

### 15.6.2 使用步骤

**步骤 1：引入依赖并配置**

```xml
<!-- 各业务服务中引入 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

```yaml
# bootstrap.yml（注意：连 Nacos 的配置要放 bootstrap.yml，优先级最高）
spring:
  application:
    name: user-service
  cloud:
    nacos:
      server-addr: localhost:8848
      config:
        file-extension: yaml     # 配置文件格式
```

**步骤 2：在 Nacos 控制台新建配置**

- Data ID：`user-service.yaml`（规则：**服务名 + 文件扩展名**）
- 配置内容（集中管理数据库、开关等）：

```yaml
# user-service.yaml（Nacos 控制台上编辑）
demo:
  message: hello from nacos config
server:
  port: 8081
```

**步骤 3：代码中动态读取**

```java
@RestController
@RequestMapping("/config")
public class ConfigController {

    // @Value 读取 Nacos 配置；加 @RefreshScope 支持动态刷新（改配置自动生效，无需重启）
    @RefreshScope
    @Value("${demo.message}")
    private String message;

    @GetMapping
    public String get() {
        return message;
    }
}
```

> 修改 Nacos 上的配置后，接口返回的内容会**自动变化**，不需要重启服务。这就是配置中心的魅力。

## 15.7 熔断降级 Sentinel

### 15.7.1 为什么要熔断：服务雪崩

场景：用户请求 → 订单服务 → 支付服务（变慢，1 秒变 5 秒）→ 订单服务的线程全部被占住 → 用户服务也拿不到订单响应 → 整个系统连环瘫痪。这就是**雪崩效应**。

对策**三件套**：

| 手段 | 含义 | 类比 |
| --- | --- | --- |
| **熔断** | 下游故障率超阈值，直接拒绝请求，不再调用 | 电路跳闸 |
| **降级** | 被拒绝的请求返回兜底结果（如"稍后重试"） | 备用方案 |
| **限流** | 每秒超过 N 个请求直接丢弃 | 限流闸门 |

### 15.7.2 Sentinel 快速接入

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>
```

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8080    # Sentinel 控制台地址（java -jar sentinel-dashboard.jar 启动）
```

**在代码中定义降级兜底**：

```java
@Service
public class OrderService {

    @Autowired
    private UserFeignClient userFeignClient;

    /**
     * fallback：user-service 不可用时返回的兜底结果（降级）
     * 注意：fallback 方法参数要与原方法一致，可多一个 Throwable
     */
    @SentinelResource(value = "getUserWithFallback",
            fallback = "getUserFallback", fallbackClass = OrderServiceFallback.class)
    public Map<String, Object> getUser(Long userId) {
        return userFeignClient.getUserById(userId);
    }
}

public class OrderServiceFallback {
    public static Map<String, Object> getUserFallback(Long userId, Throwable e) {
        return Map.of("id", userId, "name", "用户服务暂不可用（降级返回）");
    }
}
```

> 在 Sentinel 控制台配置**熔断规则**：接口异常比例超过 50% 时熔断 5 秒，期间直接走降级逻辑。
> 也可以配置**流控规则**：QPS 超过 10 时拒绝多余的请求。

### 15.7.3 隔离思想

更高级的做法是**线程池隔离 / 信号量隔离**：给每个下游分配独立线程池，一个下游耗尽线程不影响其他调用。这是阿里 `Sentinel` 和 `Resilience4j` 的核心价值，面试可以提一句即可。

## 15.8 链路追踪 Micrometer Tracing

一个请求跨多个服务，出问题时很难定位：到底是 user-service 慢，还是 order-service 慢？

**链路追踪**给每个请求分配全局唯一的 **traceId**，贯穿所有服务，串联成一条完整调用链。

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0       # 采样率 100%
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans   # Zipkin 地址
```

启动 Zipkin（`docker run -d -p 9411:9411 openzipkin/zipkin`）后，调用接口，在 Zipkin 界面即可看到完整的调用链，以及每段耗时。

> **实际项目注意**：全链路日志追踪（第十一章 MDC 的 traceId）与 Micrometer 的 traceId 可以打通，统一日志与调用链，排查问题事半功倍。

## 15.9 分布式事务（了解）

微服务各自有独立数据库，一次业务操作跨多个库，无法用数据库本地事务保证原子性。

| 方案 | 原理 | 适用场景 |
| --- | --- | --- |
| **Seata AT 模式** | 两阶段提交的改良版，业务侵入小 | 强一致（扣库存+下单） |
| **TCC** | 手动 Try/Confirm/Cancel 三阶段 | 资金类，可控性强 |
| **本地消息表** | 本地事务 + 消息队列最终一致 | 最终一致（下单+发短信） |
| **MQ 事务消息** | 半消息机制，RocketMQ 原生支持 | 异步解耦 |

> **核心结论**：分布式事务没有银弹。能用最终一致（MQ）就别用强一致（Seata），因为强一致性能差且复杂。

## 15.10 小结与练习

**本章重点**：
- 微服务 = 注册发现（Nacos）+ 远程调用（Feign）+ 网关（Gateway）+ 配置中心（Nacos Config）+ 熔断限流（Sentinel）+ 链路追踪
- 网关是唯一入口，负责鉴权/限流/路由
- 服务雪崩 → 熔断降级三件套
- 分布式事务优先考虑 MQ 最终一致

**面试题参考**：
1. 微服务优缺点？什么时候不适合微服务？
2. Nacos 和 Eureka 的区别？
3. OpenFeign 的调用过程？底层用的什么协议？（HTTP）
4. 网关的作用？Gateway 与 Zuul 的区别？
5. 什么是服务雪崩？如何解决？
6. 分布式事务有哪些方案？如何选型？

**课后练习**：
1. 用 Docker 启动 Nacos，创建 user-service（8081）和 order-service（8082）两个服务并注册。
2. order-service 用 Feign 调用 user-service 的 `/user/{id}`。
3. 创建 gateway 模块，配置 `/user/**`、`/order/**` 两条路由，通过 8080 端口访问两个服务。
4. 写一个全局过滤器，模拟校验 JWT 并把 userId 透传到下游。
5. 给 Feign 调用配置 3 秒连接超时，然后停掉 user-service，观察降级兜底结果。

上一章：[14-设计模式.md](./14-设计模式.md) | 下一章：[16-RabbitMQ.md](./16-RabbitMQ.md) | 返回目录：[README.md](./README.md)
