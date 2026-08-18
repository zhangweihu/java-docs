# 第三十二章 Spring Cloud 项目实战指南（单体拆微服务）

> 第三十一章把商城做成了单体应用，本章**按业务边界把它拆成微服务**：用户服务、商品服务、订单服务、网关，配合 Nacos、Seata、Sentinel、SkyWalking 完成注册发现、配置中心、分布式事务、熔断限流、链路追踪的全套治理。这是前 31 章所有知识点的**终极整合**。

## 32.1 从单体到微服务：为什么拆、怎么拆

### 32.1.1 单体的问题（什么时候该拆）

```
单体应用（第 31 章）的痛点：
✅ 开发简单、部署简单
❌ 团队并行开发互相阻塞（改订单要等改用户的同事）
❌ 一个模块 OOM → 全站不可用
❌ 只能整体扩容（订单压力大，用户模块也得跟着扩）
❌ 技术栈绑定（想给搜索用 ES 独立演进很难）
```

**拆微服务的判断标准**（不是所有项目都该拆）：
1. 团队人数 > 10 人，可以按服务分工
2. 模块间确实存在**独立扩展**需求（订单与用户并发量差异大）
3. 已经有能力治理（注册中心、监控、链路追踪）
4. 业务边界清晰（用户/商品/订单天然独立）

> **面试送分句**："我们是在单体做扎实后，发现订单模块和用户模块的并发量差异很大、团队要并行开发，才按业务边界拆分。"——展示你**知道什么时候不该拆**比会拆更重要。

### 32.1.2 拆分方案

```
第 31 章单体 mall 拆成 6 个工程：
├── mall-common          公共模块（Result/异常/工具类/统一依赖）
├── mall-gateway         网关（路由/鉴权/限流） 端口 8080
├── mall-user            用户服务（注册/登录/JWT） 端口 8081
├── mall-product         商品服务（商品/分类/库存） 端口 8082
├── mall-order           订单服务（下单/支付回调） 端口 8083
└── mall-auth            认证服务（可选，也可并入网关/用户）
```

**拆分原则**：
- 按**业务边界**拆（DDD 限界上下文思想，第 28 章）
- 每个服务**独立数据库**（user 库 / product 库 / order 库）—— 微服务的关键特征
- 服务间只通过 **API 调用**（OpenFeign），不共享表

## 32.2 技术选型与架构总览

### 32.2.1 技术栈（全部在前 30 章学过）

| 组件 | 用途 | 章节 |
| --- | --- | --- |
| Spring Cloud Alibaba 2023.x | 微服务全家桶 | 15 |
| Nacos | 注册中心 + 配置中心（集群） | 15、30 |
| OpenFeign | 服务间调用 | 15、30 |
| Gateway | API 网关（统一鉴权/限流/路由） | 15、30 |
| Sentinel | 熔断/限流/降级 | 15、30 |
| Seata | 分布式事务（AT 模式） | 30 |
| SkyWalking | 链路追踪 | 30 |
| Redis / MySQL / RabbitMQ | 缓存/存储/异步 | 12、16、29 |
| Docker Compose / K8s | 部署 | 19、21 |

### 32.2.2 系统架构图

```
                         ┌─────────────────────────────┐
                         │  Nacos（注册 + 配置中心，3节点）  │
                         └──────────────┬──────────────┘
                                        │ 注册/发现/配置
┌──────────┐   ┌────────────┐   ┌───────────────────────────────┐
│  用户/前端  │──►│ mall-gateway │──►│  user ──► MySQL(user)        │
└──────────┘   │ 8080       │   │  product ──► MySQL(product)+Redis │
                │ 鉴权/限流/路由 │   │  order ──► MySQL(order)+MQ    │
                └────────────┘   │   order ──► Feign ──► product    │
                                  └──────────────┬────────────────┘
                                                 │
                          ┌──────────────────────┼──────────────────┐
                          │ Seata（分布式事务）     │ Sentinel（熔断限流）  │
                          │ SkyWalking（链路追踪） │ Prometheus（监控）   │
                          └──────────────────────┴──────────────────┘
```

## 32.3 基础设施一键搭建

### 32.3.1 docker-compose 起基础设施

```yaml
version: "3.8"
services:
  mysql:
    image: mysql:8.0
    environment: { MYSQL_ROOT_PASSWORD: "123456" }
    ports: ["3306:3306"]
    volumes:
      - ./sql:/docker-entrypoint-initdb.d    # 建 3 个库 + 各表

  redis:
    image: redis:7
    command: redis-server --appendonly yes
    ports: ["6379:6379"]

  nacos:
    image: nacos/nacos-server:v2.3.2
    environment:
      MODE: cluster
      NACOS_AUTH_ENABLE: "false"
    ports: ["8848:8848", "9848:9848"]
    depends_on: [mysql]

  rabbitmq:
    image: rabbitmq:3-management
    ports: ["5672:5672", "15672:15672"]

  seata-server:
    image: seataio/seata-server:2.0.0
    environment:
      SEATA_IP: 127.0.0.1
    ports: ["8091:8091"]
    depends_on: [nacos, mysql]

  skywalking-oap:
    image: apache/skywalking-oap-server:9.7.0
    ports: ["11800:11800", "12800:12800"]

  skywalking-ui:
    image: apache/skywalking-ui:9.7.0
    ports: ["8085:8080"]
    depends_on: [skywalking-oap]
```

> **提示**：生产环境 Nacos 用 3 节点集群 + 外部 MySQL 持久化（第 30 章讲过），这里开发环境单节点足够。

### 32.3.2 mall-common 公共模块（抽公共代码）

```java
// 统一响应（从单体项目抽出来）
@Data
public class Result<T> { /* 与 31.4 相同 */ }

// 公共异常
public class BusinessException extends RuntimeException { /* 相同 */ }

// 统一异常处理器（各服务 @Import 引入）
@RestControllerAdvice
public class GlobalExceptionHandler { /* 与 31.4 相同 */ }

// 公共依赖（pom）：其他服务直接引用 mall-common
// 内含：Result、异常、工具类、Feign 公共接口等
```

**公共模块的边界**：只放"横切"的代码（响应体/异常/工具/Feign 接口），**不放业务代码**——否则公共模块会变成第二个上帝模块。

## 32.4 用户服务 user-service

### 32.4.1 配置文件（Nacos 注册 + 配置中心）

```yaml
# application.yml
spring:
  application:
    name: mall-user
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:127.0.0.1:8848}
      discovery:
        namespace: public
      config:
        file-extension: yml
        shared-configs:
          - data-id: common-db.yml     # 公共数据源配置
            refresh: true

server:
  port: 8081
```

### 32.4.2 核心代码（复用 31 章 + 微服务化）

```java
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterDTO dto) {
        userService.register(dto);
        return Result.ok(null);
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    // 内部接口：供 order-service 通过 Feign 调用（校验用户是否存在）
    @GetMapping("/internal/{id}")
    public Result<UserInfoVO> getUserById(@PathVariable Long id) {
        return Result.ok(userService.getUserById(id));
    }
}
```

### 32.4.3 服务间调用的安全（内部接口防暴露）

```
✅ 内部接口用 /internal/** 前缀 + 独立鉴权（X-Internal-Token）
✅ 网关路由只暴露 /api/user/** 对外，/internal/** 不允许外部访问
✅ 内部调用校验请求头 token，防绕过网关直连
```

## 32.5 商品服务 product-service

### 32.5.1 提供 Feign 接口（给订单服务调用）

```java
// mall-common 中定义公共 Feign 接口（服务提供方实现，消费方继承）
@FeignClient(name = "mall-product", path = "/api/product/internal")
public interface ProductFeignClient {

    @GetMapping("/{id}")
    Result<ProductVO> getById(@PathVariable("id") Long id);

    @PostMapping("/deduct")
    Result<Void> deductStock(@RequestBody DeductDTO dto);   // 扣库存
}
```

```java
// product-service 实现（Controller 实现 Feign 接口，天然满足契约）
@RestController
public class ProductFeignController implements ProductFeignClient {

    private final ProductService productService;

    @Override
    public Result<ProductVO> getById(Long id) {
        return Result.ok(productService.getDetail(id));
    }

    @Override
    public Result<Void> deductStock(DeductDTO dto) {
        productService.deductStock(dto);    // 乐观扣库存（31.6 节逻辑）
        return Result.ok(null);
    }
}
```

**这样设计的好处**：Feign 接口定义在公共模块，**提供方和消费方共用同一份契约**，改接口签名时编译期就能发现两边不一致（第 28 章 DIP：依赖抽象）。

## 32.6 订单服务 order-service（微服务的核心难点）

### 32.6.1 调用商品服务扣库存（Feign + 超时 + 熔断）

```yaml
# order-service 调用配置（第 30 章强调的生产标配）
feign:
  client:
    config:
      default:
        connect-timeout: 2000      # 连接超时 2s
        read-timeout: 3000         # 读取超时 3s

resilience4j:                      # 或 Sentinel 熔断（30.5）
  circuitbreaker:
    instances:
      productService:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
```

```java
@Service
public class OrderServiceImpl implements OrderService {

    private final ProductFeignClient productFeignClient;
    private final OrderMapper orderMapper;

    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    public Order createOrder(OrderCreateDTO dto, Long userId) {
        // 1. Feign 调用商品服务扣库存（参与全局事务）
        Result<Void> result = productFeignClient.deductStock(
                new DeductDTO(dto.getProductId(), dto.getNum()));
        if (result.getCode() != 0) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        // 2. 本地落订单
        Order order = buildOrder(dto, userId);
        orderMapper.insert(order);
        // 3. 发 MQ 通知（异步解耦）
        mqTemplate.convertAndSend("order.created", order.getId());
        return order;
    }
}
```

### 32.6.2 分布式事务回滚验证（Seata）

```
场景：订单服务调用商品服务扣库存成功 → 本地插订单失败（如字段超长）
结果：Seata 检测到全局事务异常 → 通知商品服务回滚
      → 商品服务 undo_log 生成反向 SQL：库存 +N 恢复

验证命令：
1. 正常下单 → 查订单 + 库存都正确
2. 故意让订单插入抛异常 → 查库存已恢复（回滚成功）
3. 查看 Seata 控制台：全局事务 XID 状态（Rollbacked）
```

### 32.6.3 订单状态机的异步流转

```
下单 ──► 待支付 ──支付回调──► 已支付 ──发货──► 已发货 ──确认收货──► 已完成
            │
            └──超时30分钟未支付──► 已取消（MQ 延迟消息，第 16 章）
```

**微服务 vs 单体的差异**：订单状态变化后，需要**发事件**让其他服务联动（如已支付 → 通知库存锁定、通知用户服务加积分）。**服务间通过 MQ 异步解耦**，而不是同步调用。

## 32.7 网关 gateway-service

### 32.7.1 路由配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-route
          uri: lb://mall-user          # lb:// 负载均衡到 Nacos 里的服务
          predicates:
            - Path=/api/user/**
          filters:
            - StripPrefix=1            # /api/user/xxx → 转发 user 服务 /xxx
        - id: product-route
          uri: lb://mall-product
          predicates:
            - Path=/api/product/**
          filters:
            - StripPrefix=1
        - id: order-route
          uri: lb://mall-order
          predicates:
            - Path=/api/order/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter   # 订单接口限流（30.4）
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100
```

### 32.7.2 全局鉴权（复用第 30 章 AuthGlobalFilter）

```
网关统一校验 JWT → 校验通过把 userId 放入 Header（X-User-Id）→ 转发下游
下游服务信任网关传过来的 X-User-Id（内网服务间不再重复校验）

安全边界：
✅ 外部流量只能进网关（只暴露 8080）
✅ 下游服务不对外暴露（Nacos 注册但不映射公网端口）
```

## 32.8 前端联调与 API 规范

### 32.8.1 统一 API 规范（多服务协作必备）

| 规范 | 约定 |
| --- | --- |
| 路径 | `/api/{服务}/{资源}`，如 `/api/order/create` |
| 返回 | 统一 `Result<T>`：`{code, message, data}` |
| 错误码 | 0 成功；400 参数；401 未登录；403 无权限；429 限流；500 系统 |
| 分页 | 统一 `PageVO<T>{total, current, size, records}` |
| 时间 | 统一字符串 `yyyy-MM-dd HH:mm:ss`，UTC 存储 |
| 鉴权 | 前端带 `Authorization: Bearer <token>`，网关解析 |

### 32.8.2 前端跨服务聚合

**一个页面可能调多个服务**（订单详情 = 订单 + 商品 + 用户信息），两种做法：

```
方案 A：前端发 3 个请求（简单，但多次往返）
方案 B：网关层 BFF 聚合（聚合接口，一次请求返回全部）
方案 C：订单服务 Feign 聚合（订单服务调商品/用户，拼装 VO）← 本系列推荐
```

## 32.9 可观测性与监控

### 32.9.1 链路追踪（SkyWalking）

```bash
# 每个服务启动加 agent（第 30 章，业务零侵入）
java -javaagent:/opt/skywalking-agent.jar \
     -Dskywalking.agent.service_name=mall-order \
     -jar order-service.jar
```

**验证**：登录商城 → 下单 → 打开 SkyWalking UI（8085）→ 搜索 traceId → 看到完整链路：

```
GET /api/order/create
 └── mall-order: 12ms
     ├── mall-product /deduct: 5ms     ← 哪段慢一目了然
     ├── MySQL 订单插入: 3ms
     └── RabbitMQ send: 1ms
```

### 32.9.2 监控告警（Prometheus + Grafana）

```yaml
# 每个服务引入 actuator + micrometer
# 统一暴露 /actuator/prometheus
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

**核心看板指标**（第 30 章 RED 法）：
- 各服务 QPS、错误率、P99 耗时
- 网关限流拒绝次数（RateLimiter）
- Sentinel 熔断事件数
- JVM 内存/GC（第 24 章）

## 32.10 部署与 CI/CD

### 32.10.1 docker-compose 全栈部署

```yaml
# 在 32.3 基础上加服务编排
services:
  mall-gateway:
    build: ./gateway
    ports: ["8080:8080"]
    depends_on: [nacos]
    environment:
      NACOS_ADDR: nacos:8848
  mall-user:
    build: ./user-service
    ports: ["8081:8081"]          # 生产可不映射，仅内网
    depends_on: [nacos, mysql]
  mall-product:
    build: ./product-service
    ports: ["8082:8082"]
    depends_on: [nacos, mysql, redis]
  mall-order:
    build: ./order-service
    ports: ["8083:8083"]
    depends_on: [nacos, mysql, rabbitmq, seata-server]
```

### 32.10.2 多环境配置（dev/test/prod）

```
✅ 每个服务 3 套配置放 Nacos：
   mall-user-dev.yml / mall-user-test.yml / mall-user-prod.yml
✅ namespace 隔离环境（dev/public、prod/private）
✅ 数据库连接、密码等敏感项用环境变量注入，不写进配置文件
```

### 32.10.3 发布流程（第 21 章 CI/CD 落地）

```
代码提交 → Jenkins 多分支流水线
├── 构建每个服务 jar
├── 打包每个服务镜像（带版本 tag）
├── 推送到镜像仓库
└── 通知服务器拉取 → 滚动更新（先更新订单，验证后再更新其他）
回滚：镜像 tag 指回上一个版本
```

## 32.11 全链路压测与优化

### 32.11.1 压测流程

```
JMeter 压测下单接口（100 并发 × 10 分钟）
→ 观察 SkyWalking：哪段调用耗时最高
→ 观察 Prometheus：哪个服务 CPU/GC 异常
→ 观察 Sentinel 控制台：限流/熔断是否触发
→ 定位瓶颈 → 优化（缓存/索引/异步/扩容）→ 再压
```

### 32.11.2 常见瓶颈与优化对照表

| 瓶颈 | 表现 | 优化手段 | 章节 |
| --- | --- | --- | --- |
| 商品详情 DB 压力大 | 商品接口慢 | Redis 缓存 + 防击穿 | 29、31 |
| 扣库存超卖 | 并发下单库存错 | 乐观锁 + Seata | 30、31 |
| MQ 消费慢 | 订单状态更新延迟 | 消费者批量 + 分区 | 16、27 |
| Feign 超时 | 链路慢 | 超时配置 + 熔断降级 | 30 |
| 单库单表瓶颈 | 数据量大查询慢 | 分库分表 | 25 |

## 32.12 项目总结与面试讲解指南

### 32.12.1 项目亮点清单

1. **拆分有依据**：按业务边界拆 6 个服务，每个服务独立库独立部署
2. **注册发现**：Nacos 集群，服务优雅上下线，Feign 负载均衡
3. **配置中心**：Nacos 动态刷新 + namespace 环境隔离 + 本地兜底
4. **网关治理**：统一 JWT 鉴权 + Redis 限流 + 路由 + 内部接口隔离
5. **分布式事务**：Seata AT 模式，下单扣库存全局回滚
6. **熔断降级**：Sentinel 按接口限流，Feign 超时熔断，降级兜底
7. **可观测**：SkyWalking 全链路 + Prometheus 指标 + traceId 日志
8. **异步解耦**：RabbitMQ 事件驱动，订单与通知解耦

### 32.12.2 面试常问

| 问题 | 回答要点 |
| --- | --- |
| 为什么拆微服务？怎么拆？ | 团队规模、独立扩展需求、按业务边界、独立数据库 |
| 服务间怎么调用？ | OpenFeign + Nacos 负载均衡，超时/熔断配置 |
| 分布式事务怎么解决？ | Seata AT（自动回滚）+ MQ 最终一致（异步场景） |
| 网关做了什么？ | 统一鉴权、限流、路由、跨域，业务逻辑不下沉网关 |
| 一个服务挂了怎么办？ | 熔断降级、服务隔离、SkyWalking 快速定位、预案 |
| 服务间数据不一致怎么办？ | 最终一致 + 对账任务（第 30 章） |
| 和单体比有什么缺点？ | 运维复杂、分布式事务难、调试难——**要诚实说出权衡** |
| 如果让你重做，会改什么？ | 先做模块化单体、引入事件驱动更早、加强测试 |

### 32.12.3 面试项目话术模板（3 分钟版）

> "我做了一个电商系统，从单体演进到微服务。单体阶段实现了完整的业务闭环（用户、商品、订单、缓存、安全）；当团队需要并行开发、订单和商品并发差异明显后，按业务边界拆成 6 个服务。技术栈是 Spring Cloud Alibaba：Nacos 做注册和配置，Gateway 做统一鉴权限流，OpenFeign 做服务调用，Sentinel 做熔断，Seata 解决下单的分布式事务，SkyWalking 做链路追踪。项目里我重点解决了三个难点：一是并发下单的防超卖，用乐观扣减加分布式锁；二是跨服务的下单事务，用 Seata AT 模式自动回滚；三是缓存一致性，用 Cache Aside 加 TTL 兜底。部署上走 Docker Compose 加 Jenkins 流水线，监控用 Prometheus 和 SkyWalking。"

> **讲项目的心法**：不要背代码，讲**问题 → 方案 → 权衡**。面试官想听的是你**为什么这么选**，不是你会写多少行代码。

## 32.13 小结与练习

**本章重点**：
- 拆微服务的时机与依据：团队规模 + 独立扩展 + 清晰边界
- 公共模块 mall-common：只放横切代码，不放业务
- 独立数据库是微服务的分水岭
- Feign 契约在公共模块定义：提供方实现、消费方继承
- Seata AT 全局回滚验证流程
- 网关是唯一对外入口：鉴权/限流/内部接口隔离
- 异步事件驱动：服务间解耦的核武器
- SkyWalking 验证完整调用链
- 讲项目的框架：问题 → 方案 → 权衡

**面试题参考**：
1. 单体什么时候该拆微服务？有哪些权衡？
2. 微服务独立数据库，跨库怎么查？（应用层聚合/冗余字段/ES）
3. Feign 接口为什么定义在公共模块？
4. 服务间调用失败怎么办？重试有哪些坑（幂等）？
5. Seata AT 模式和 TCC 的区别？为什么选 AT？
6. 网关怎么防止内部接口被绕过？
7. 微服务如何保证消息不丢不重（第 27 章）？
8. 一个服务挂了，怎么保证其他服务不受影响？
9. 你们微服务的部署和发布流程？
10. 如果让你从零设计一个微服务架构，核心考虑什么？

**课后练习**：
1. 用 32.3 的 docker-compose 把基础设施全部起起来。
2. 按拆分方案创建 6 个工程（common/gateway/user/product/order），跑通注册登录。
3. 让 order 通过 Feign 调用 product 扣库存，验证链路。
4. 集成 Seata，故意让订单插入失败，验证库存回滚。
5. 集成 SkyWalking，下单后查看完整调用链和耗时分布。
6. 用 JMeter 压测下单接口，找瓶颈并优化（缓存/索引）。
7. 用 32.12.3 的话术模板，向别人（或录音）讲一遍你的项目。

**全系列收官语**：从第一章的 `Hello World` 到第三十二章的微服务实战，你已完整走过"**语法 → 面向对象 → 集合 → 并发 → IO → Web → 微服务 → 中间件 → 容器化 → 调优 → 设计 → 实战**"的企业级 Java 全栈之路。技术会更新（Spring Boot 3、JDK 21 已在路上），但**解决问题的能力、代码规范意识、系统设计思维**才是你真正的核心竞争力。继续保持"多敲代码、多问为什么、多复盘"，你会成为一名真正的架构师。

上一章：[31-SpringBoot项目实战.md](./31-SpringBoot项目实战.md) | 返回目录：[README.md](./README.md)
