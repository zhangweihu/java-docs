# 第三十七章 Nacos 系统学习（注册中心与配置中心从原理到实践）

> 本章目标：系统掌握 Nacos——它是什么、为什么服务发现走 AP 而配置管理走 CP、临时/持久实例与健康检查机制、2.x 的 gRPC 长连接架构、配置中心长轮询与命名空间/分组/Data ID 三级隔离、生产三节点集群 + MySQL 持久化部署，以及安全加固与常见坑。与第十五章（入门用法）、第三十章（治理深度）、第三十二章（项目实战）互补：**15 章学会用，30 章学会治理，本章吃透原理与生产部署**。
>
> 前置知识：第十五章 Spring Cloud（Nacos 入门用法）、第三十章微服务治理实战、第三十五章 Docker 系统学习。

## 37.1 Nacos 是什么：不止是注册中心

### 37.1.1 一句话定义

**Nacos（Naming and Configuration Service）** 是阿里巴巴开源的一站式微服务基础设施，核心是两大能力：

```
Nacos = 注册中心（Naming Service） + 配置中心（Configuration Service） + 动态 DNS + 管理平台
         服务找到服务（服务发现）          配置集中管理（动态刷新）
```

| 能力 | 解决什么问题 | 类比 |
| --- | --- | --- |
| 服务注册与发现 | 服务地址动态变化，调用方怎么找到对方？ | 通讯录 / 黄页 |
| 动态配置管理 | 配置散落各处，改配置要重启 N 个服务？ | 远程遥控器 |
| 动态 DNS 服务 | 按域名解析到服务实例（DNS 协议接入） | 智能 DNS |
| 管理平台 | 网页可视化查看服务、配置、命名空间 | 控制台 |

### 37.1.2 为什么 Nacos 成为国内事实标准

| 对比项 | Nacos | Eureka | Consul | Zookeeper |
| --- | --- | --- | --- | --- |
| 一致性模型 | 注册 AP / 配置 CP | AP | 注册 AP（consul 配置为 CP） | CP |
| 注册中心 + 配置中心 | **二合一** | 只有注册 | 二合一（有 KV） | 仅协调（做注册/配置要自己封装） |
| 健康检查 | 心跳 + 主动探测 | 只靠心跳 | 主动探测 | 会话 + 心跳 |
| 临时/持久实例 | 都支持 | 只支持临时 | 主动注册（类持久） | 临时节点 |
| 协议 | 2.x gRPC；AP 用 Distro，CP 用 Raft | 自研 HTTP | Raft（一致性）+ gossip | ZAB |
| 控制台 | 强大（服务/配置/命名空间/权重/灰度） | 弱 | 中 | 弱 |
| 维护状态 | 阿里活跃维护 | 2.x 已停更（维护模式） | HashiCorp 维护 | Apache 基金会 |
| 生态 | Spring Cloud Alibaba 官方 | Spring Cloud Netflix（已迁移） | 通用 | 大数据生态强 |

> **结论**：Java 微服务场景下，Nacos 是"注册 + 配置"二合一的最优解；Zookeeper 的强项是分布式协调（第 23 章）；K8s 环境还有 DNS-based 方案（CoreDNS），但 Nacos 在服务治理（权重、灰度、上下线）上依然不可替代。

### 37.1.3 1.x 与 2.x 的架构升级（面试必问）

| 对比项 | 1.x | 2.x |
| --- | --- | --- |
| 客户端与 Server 通信 | HTTP 短连接（1.4 才有少量 gRPC） | **gRPC 长连接**（核心链路） |
| 服务变更推送 | UDP 推送（易丢包，可靠性差） | **gRPC 主动推送**，可靠 |
| 连接数 | 每客户端多次 HTTP 连接，服务端压力大 | 长连接复用，连接数大幅下降 |
| 性能 | 万级实例尚可 | 支持**百万级实例**（阿里云压测） |
| 配置管理 | HTTP 长轮询 | gRPC 双向流 + 长轮询兜底 |

> **核心认知**：2.x 用 **gRPC 长连接** 取代了 1.x 的 HTTP 短连接 + UDP 推送，服务列表变更**由 Server 主动推给客户端**，实时性从"秒级轮询"提升到"毫秒级推送"。注意 8848 端口外新增了 **9848（gRPC）、9849（gRPC 集群）** 端口，防火墙必须放行（第 15 章启动命令已体现）。

## 37.2 注册中心原理：AP 与 CP 双模型

### 37.2.1 为什么注册用 AP、配置用 CP（CAP 视角）

```
CAP 定理：一致性(C)、可用性(A)、分区容错(P) 三者最多取其二
（网络分区必然发生，所以 P 必选，实际是在 C 和 A 之间权衡）

服务发现（注册中心）──► 选 AP：宁可读到旧列表，也不能"找不到服务"
                          （消费者本地有缓存兜底，最终一致即可）
配置管理（配置中心）──► 选 CP：配置必须一致，不能出现"一个服务读到新配置、
                          另一个读到旧配置"（改了数据库密码必须全部生效）
```

**Nacos 的聪明之处**：同一套系统，按场景分别使用不同协议：

| 场景 | 协议 | 数据 |
| --- | --- | --- |
| 临时实例（服务发现） | **Distro**（自研，AP） | 注册表存内存，节点间异步同步 |
| 持久实例（服务发现） | **Raft**（基于 JRaft，CP） | 写入需多数节点确认 |
| 配置管理（配置中心） | **Raft**（CP） | 配置变更必须多数派提交 |

### 37.2.2 Distro 协议：AP 模式的注册表同步

```
Client ──► Nacos-A（注册）──► Nacos-A 内存注册表
                                 │ 异步同步（Snapshot + 增量）
                                 ▼
                       Nacos-B、Nacos-C 内存注册表
                 （最终一致：可能短暂不一致，但很快收敛）
```

- 注册/心跳写入**任意节点**，该节点承担"这个实例的主节点"角色；
- 节点间通过**异步复制**（定时快照 + 增量校验）收敛，最终一致；
- 节点挂了不影响其他节点继续服务（可用性优先）。

### 37.2.3 临时实例 vs 持久实例（重要概念）

| 对比项 | 临时实例（ephemeral=true） | 持久实例（ephemeral=false） |
| --- | --- | --- |
| 生命周期 | 客户端心跳，30 秒没心跳即删除 | 手动注册后持久存在，需手动注销 |
| 健康检查 | 客户端上报心跳（5 秒一次） | Server 主动探测（HTTP/TCP） |
| 一致性协议 | Distro（AP） | Raft（CP） |
| 适用场景 | **默认推荐**：动态扩缩容的微服务 | 固定实例（如数据库、Redis、自研中间件） |
| 故障恢复 | 心跳恢复自动重新注册 | 需保证自身可探测 |

```bash
# 手动注册一个持久实例（例如注册一个 MySQL 实例）
curl -X POST 'http://localhost:8848/nacos/v1/ns/instance?serviceName=mysql-master&ip=10.0.0.5&port=3306&ephemeral=false'
```

> Spring Cloud Alibaba 默认注册的就是**临时实例**，微服务场景无需改。持久实例多用于把中间件、固定节点纳入 Nacos 统一管理。

### 37.2.4 健康检查的两种方式

```
临时实例：Client ──每 5 秒心跳──► Server
          超过 15 秒未收到 → 标记"不健康"
          超过 30 秒未收到 → 从注册表删除（ap）

持久实例：Server ──主动探测（HTTP/TCP）──► Client
          连续失败达到阈值 → 标记不健康（cp）
```

> 与 Eureka 的对比记忆：Eureka 只有客户端心跳（AP），没有主动探测；Nacos 的主动探测让"死进程但端口还活着"的假死实例也能被识别（通过自定义 health check 路径）。

### 37.2.5 服务发现全流程（2.x）

```
Provider 启动
   │  1. 向 Nacos 注册：serviceName + ip:port + weight + namespace
   ▼
Nacos Server（gRPC 长连接建立）
   │  2. 写入注册表，标记健康
   ▼
Consumer 启动
   │  3. 订阅服务：向 Nacos 建立 gRPC 订阅（Subscriber）
   ▼
Nacos Server
   │  4. 推送实例列表（首次全量，之后增量）
   ▼
Consumer 本地缓存（进程内内存缓存，含"兜底机制"）
   │  5. 业务调用：Nacos 客户端负载均衡（轮询/权重/随机）选一个实例
   ▼
发起 HTTP/gRPC 调用
```

**容灾兜底（面试加分点）**：即使 Nacos Server 全部宕机，消费者本地缓存的实例列表依然可用（`failover` 目录下的容灾文件可手工干预），服务不会立刻中断——这就是 AP 模式 + 客户端缓存的意义。

## 37.3 注册中心核心概念与治理能力

### 37.3.1 命名空间（Namespace）、分组（Group）、服务（Service）三级隔离

```
Namespace（环境隔离：dev / test / prod）   ← 最外层，物理隔离，互不可见
   └── Group（业务分组：DEFAULT_GROUP / PAY_GROUP）  ← 逻辑隔离
        └── Service（服务名：order-service）          ← 具体服务
             └── Cluster（集群：杭州 / 上海，可配置集群路由）
                  └── Instance（实例：ip:port + 权重）
```

| 层级 | 隔离力度 | 典型用法 |
| --- | --- | --- |
| Namespace | 强隔离（注册表完全分开） | **环境隔离**：dev/test/prod 各一个 namespace |
| Group | 逻辑隔离 | 同环境不同业务线，或同服务不同版本 |
| Service | 服务维度 | 一个业务服务 |
| Cluster | 容灾/就近 | 同城双机房、异地多活 |

```yaml
# 客户端指定 namespace（用 ID，不是名称！）
spring:
  cloud:
    nacos:
      discovery:
        namespace: dev-namespace-id    # 控制台"命名空间"里复制 ID
        group: DEFAULT_GROUP
```

> **深坑提醒**：namespace 要填**命名空间的 ID**（一串字符串），不是展示名。填错 namespace/group 会导致"服务注册上了但消费方订阅不到"——这是 Nacos 排障第一大坑。

### 37.3.2 权重与负载均衡

- 每个实例可设置 `weight`（0~100，默认 1）；
- 客户端负载均衡（Nacos 内置或配合 Spring Cloud LoadBalancer）按权重分配流量；
- **权重设为 0 的实例不接收流量**——经典用途：**上线新版本先权重 0 观察，再逐步放量（金丝雀/灰度）**；下线时先权重 0 排空流量再停（衔接 30.2.2 优雅下线）。

```bash
# 把某个实例权重调为 0（不接收新流量）
curl -X PUT 'http://localhost:8848/nacos/v1/ns/instance?serviceName=order-service&ip=10.0.0.2&port=8082&weight=0&ephemeral=true'
```

### 37.3.3 保护阈值（Protect Threshold）

场景：健康实例数量骤减（如只剩 1/10），若严格只把流量分给健康实例，健康实例会被瞬间打爆。

**保护阈值**：健康实例比例低于阈值（如 0.8）时，Nacos 会把**不健康实例也纳入返回列表**（按比例兜底），宁可部分请求打到可能不健康的实例，也不让健康实例过载——用"少部分失败"换取"整体可用"。

```bash
# 控制台 → 服务详情 → 保护阈值 填 0.8
```

### 37.3.4 优雅上下线（衔接 30.2.2，复习巩固）

```
上线：先注册（权重 0）→ 健康检查通过 → 权重调为正常 → 开始接流量
下线：权重调 0（排空存量）→ 主动 deregister → 等待消费者感知 → 停止进程
```

> 生产双保险：应用内 `@PreDestroy` 优雅注销 + K8s `preStop` 钩子 + Nacos 权重归零，三管齐下避免"请求打到正在下线的实例"。

## 37.4 配置中心原理与进阶

### 37.4.1 配置模型：Data ID、Group、Namespace

| 概念 | 作用 | 示例 |
| --- | --- | --- |
| Data ID | 配置唯一标识（一般 = 服务名-环境.后缀） | `order-service-dev.yaml` |
| Group | 配置分组 | `DEFAULT_GROUP` |
| Namespace | 环境隔离 | dev / prod |

```
Namespace（prod）
   └── Group（DEFAULT_GROUP）
        └── Data ID：user-service-prod.yaml
        └── Data ID：common-datasource.yaml（公共配置）
```

**公共配置共享**（Spring Cloud 中 `shared-configs` / `extension-configs`）：

```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        file-extension: yaml
        shared-configs:                    # 多服务共享的公共配置
          - data-id: common-datasource.yaml
            refresh: true                  # 公共配置也支持动态刷新
        extension-configs:
          - data-id: user-service-extra.yaml
            refresh: true
```

### 37.4.2 动态刷新原理：长轮询（Long Polling）

```
客户端 ConfigService ──发起长轮询──► Nacos Server
        ▲                                │
        │  ① 请求挂起（最长 30 秒）          │ ② 期间配置变更？
        │  ③ 变更 → 立即返回（含变更内容）    │ ④ 无变更 → 30 秒超时返回（空）
        │                                  ▼
        └── 收到结果，比对本地，刷新配置 ◄── ⑤ 客户端再次发起长轮询（循环）
```

- 不是"轮询 30 秒一次"，而是**每次请求挂起最长 30 秒，有变更秒级返回**；
- 客户端本地缓存 + MD5 比对：服务端返回的配置带 MD5，相同则不处理，不同则拉取全量；
- 变更通知链路：**控制台修改 → 服务端发布（触发 Listener）→ 长轮询请求立即返回 → 客户端更新本地 → 应用 `@RefreshScope` Bean 重建**。

### 37.4.3 配置管理的完整能力

| 能力 | 说明 |
| --- | --- |
| 动态刷新 | 改配置不用重启，秒级生效 |
| 历史版本 | 每个配置保留历史，可一键回滚（最多保留 10 个版本） |
| 灰度发布 | Beta 发布：只对指定 IP 的客户端生效，验证后再全量 |
| 监听查询 | 查看配置被哪些客户端订阅（用于灰度/排查） |
| 导入导出 | 控制台批量导出 yml/zip，便于环境间迁移 |
| 命名空间克隆 | 一键把 dev 的配置克隆到 test/prod |

```yaml
# 配置示例（order-service-dev.yaml）：集中管理，动态生效
server:
  port: 8083

spring:
  datasource:
    url: jdbc:mysql://10.0.0.5:3306/mall_order?useUnicode=true
    username: mall
    password: ENC(xxxx)          # 敏感信息建议加密

mall:
  order:
    timeout-seconds: 60          # 业务开关，改这里立即生效
```

### 37.4.4 Java 集成与刷新注意事项（衔接 15.6 与 30.3）

```java
@RefreshScope                    // 配置变更后重建 Bean
@Component
public class OrderConfig {
    @Value("${mall.order.timeout-seconds:30}")
    private int timeoutSeconds;

    @NacosValue(value = "${mall.order.timeout-seconds:30}", autoRefreshed = true)
    private int timeoutNacos;     // 原生 Nacos 注解，同样支持自动刷新
}
```

**刷新失效的坑（面试高频）**：

| 写法 | 能否动态刷新 | 原因 |
| --- | --- | --- |
| `@RefreshScope` + `@Value` 字段 | ✅ | Bean 重建时重新注入 |
| `static` 字段 | ❌ | Bean 重建不重新赋 static |
| 构造器注入 / `@ConfigurationProperties` 未加 RefreshScope | ❌ | 实例不重建 |
| `@RefreshScope` 类里 new 出的普通类 | ❌ | 未被代理管理 |

> 记忆口诀：**要让配置"活"，Bean 必须被 `@RefreshScope` 管住，且值通过 `@Value`/`@NacosValue` 注入**。

## 37.5 集群部署：生产三节点 + MySQL

### 37.5.1 为什么生产必须集群 + MySQL

- **单机（内嵌 Derby）**：只能用于开发；Derby 数据不持久（数据在 `data/` 目录）、不支持多节点；
- **生产标准**：≥ 3 节点 Nacos + **外部 MySQL**（配置、注册数据持久化；Raft 元数据也存 MySQL）；
- 客户端连接任意节点都行（2.x 通过 gRPC 自动重连到健康节点）。

### 37.5.2 集群架构图

```
                    ┌───────────────────────┐
                    │   MySQL 8（主从或单主）  │   ← 配置/持久数据落库
                    └───────────┬───────────┘
             ┌──────────────────┼──────────────────┐
             ▼                  ▼                  ▼
      ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
      │ Nacos Node 1 │   │ Nacos Node 2 │   │ Nacos Node 3 │
      │ 8848/9848   │   │ 8848/9848   │   │ 8848/9848   │
      └─────────────┘   └─────────────┘   └─────────────┘
             ▲                  ▲                  ▲
             └──────── SLB（云负载均衡 / Nginx）────┘
                                  │
                             客户端 / 业务服务
```

### 37.5.3 三节点部署（Docker Compose 示例）

```yaml
# nacos-cluster.yml（先初始化 MySQL：执行 conf/mysql-schema.sql）
version: '3.8'

services:
  nacos-mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: nacos
      MYSQL_ROOT_PASSWORD: root123
    ports:
      - "3306:3306"
    volumes:
      - ./nacos.sql:/docker-entrypoint-initdb.d/nacos.sql:ro   # 官方 schema 脚本

  nacos1: &nacos
    image: nacos/nacos-server:v2.3.2
    environment:
      MODE: cluster
      NACOS_SERVERS: nacos1:8848,nacos2:8848,nacos3:8848
      SPRING_DATASOURCE_PLATFORM: mysql
      MYSQL_SERVICE_HOST: nacos-mysql
      MYSQL_SERVICE_DB_NAME: nacos
      MYSQL_SERVICE_USER: root
      MYSQL_SERVICE_PASSWORD: root123
      NACOS_AUTH_ENABLE: "true"               # 开启鉴权（生产必开）
      NACOS_AUTH_TOKEN: <自定义>base64<超过32字节>
      NACOS_AUTH_IDENTITY_KEY: serverIdentity
      NACOS_AUTH_IDENTITY_VALUE: security
    ports: ["8848:8848", "9848:9848"]
    depends_on: [nacos-mysql]

  nacos2:
    <<: *nacos
    ports: ["8849:8848", "9849:9848"]         # 演示多端口，生产各节点独立主机

  nacos3:
    <<: *nacos
    ports: ["8850:8848", "9850:9848"]
```

```bash
docker compose -f nacos-cluster.yml up -d
# 访问任一节点控制台：http://localhost:8848/nacos
# 集群健康：/nacos/v1/console/health/liveness（返回 UP 表示该节点正常）
```

> **版本升级注意**：1.x 升级 2.x 后，客户端端口要放行 **9848（gRPC）**，且 2.x 与 1.x 客户端**不能混用**在同一个集群（有兼容模式，但不建议生产混跑）。

### 37.5.4 关键配置参数

| 参数 | 说明 |
| --- | --- |
| `NACOS_SERVERS` | 集群节点列表（节点间通信） |
| `SPRING_DATASOURCE_PLATFORM=mysql` | 使用外部 MySQL |
| `NACOS_SERVER_MODE` / `MODE=cluster` | 集群模式 |
| `NACOS_AUTH_ENABLE=true` | 开启控制台/API 鉴权 |
| `NACOS_CORE_AUTH_PLUGIN_NACOS_TOKEN_SECRET_KEY` | 鉴权 Token（Base64，>32 字节） |
| `NACOS_AUTH_IDENTITY_KEY/VALUE` | 服务端身份认证，防止绕过 |

### 37.5.5 容灾与运维

- **节点故障**：AP 模式下其余节点继续服务，故障节点恢复后自动同步（Distro 收敛）；
- **MySQL 故障**：读操作降级、写操作受限——所以 MySQL 也要做主从高可用；
- **客户端容灾**：本地缓存 + failover 文件兜底（37.2.5）；
- 日常运维：控制台查看服务健康度、权重调整、配置回滚、导入导出做环境迁移。

## 37.6 Spring Boot / Cloud 集成要点（衔接 15 章）

### 37.6.1 依赖与配置速查（Spring Cloud Alibaba 2023.x）

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

```yaml
# application.yml（注册发现）
spring:
  application:
    name: order-service
  cloud:
    nacos:
      server-addr: localhost:8848
      discovery:
        namespace: prod-ns-id
        group: DEFAULT_GROUP
        weight: 1
        # 自定义健康检查路径（可选）
        # health-check-url: /actuator/health
  config:
    import: optional:nacos:order-service.yaml   # Spring Cloud 2020+ 推荐方式（替代 bootstrap.yml）
```

> Spring Cloud 2020+ 后 `bootstrap.yml` 默认关闭，用 `spring.config.import` 引入 Nacos 配置；老项目用 `spring-cloud-starter-bootstrap` 可继续 bootstrap 方式（第 15 章示例即 bootstrap 风格，注意二选一）。

### 37.6.2 调用与负载均衡

```java
// 方式一：@LoadBalanced RestTemplate（按服务名调用，Nacos 提供实例列表）
@Bean
@LoadBalanced
public RestTemplate restTemplate() { return new RestTemplate(); }

// 方式二：OpenFeign（推荐，第 15 章已详述）
@FeignClient(name = "user-service")
public interface UserFeignClient { ... }
```

### 37.6.3 多环境切换实践

```
方案：一套代码，N 套环境
├── Namespace 隔离：dev-ns / test-ns / prod-ns（注册表 + 配置全隔离）
├── 服务配置：application-{profile}.yml 里指定 namespace/group
└── 启动：java -jar app.jar --spring.profiles.active=dev
         或 -Dspring.cloud.nacos.discovery.namespace=dev-ns-id
```

> **生产建议**：环境用 **Namespace** 隔离（强隔离），业务线用 **Group** 隔离（逻辑隔离），同名服务在不同环境互不可见，杜绝"dev 的订单打到 prod 的库存"这类事故。

## 37.7 安全与生产实践

### 37.7.1 安全加固清单

| 项目 | 做法 |
| --- | --- |
| 开启鉴权 | `NACOS_AUTH_ENABLE=true`，默认账号 nacos/nacos 必须改密码 |
| Token 加固 | Token > 32 字节且 Base64，定期轮换；设置 `NACOS_AUTH_TOKEN_TTL` |
| 身份认证 | 配置 `NACOS_AUTH_IDENTITY_KEY/VALUE`，防止请求绕过鉴权 |
| 网络隔离 | 控制台/Admin API 不暴露公网；只对服务网段开放 8848/9848 |
| 敏感配置 | 数据库密码等用加密插件（如 Jasypt）或外部密钥管理（Vault） |
| 最小权限 | 只给需要的团队分配命名空间管理权限 |

### 37.7.2 高频排查清单（生产必备）

| 症状 | 排查思路 |
| --- | --- |
| 服务注册了但调用方找不到 | namespace/group 不一致；服务名不一致；端口 9848 未放行 |
| 配置改了不生效 | 检查 `@RefreshScope`；检查 Data ID/Group/namespace 是否正确；import 方式是否生效 |
| 控制台能看到实例但调用超时 | 实例权重为 0；健康检查路径错误被标记不健康；防火墙 |
| 集群节点一直报错 | `NACOS_SERVERS` 配置错误；MySQL 连接失败；端口 9849 未放行 |
| 鉴权开启后客户端连接失败 | 客户端版本过旧不支持鉴权；Token 与 Server 不一致 |

### 37.7.3 配置管理最佳实践（衔接 30.3）

```
✅ 公共配置（数据源/Redis/MQ 地址）→ shared-configs 统一管理
✅ 服务专属配置 → 本服务 Data ID（服务名-环境.yaml）
✅ 命名规范 → {服务名}-{profile}.{格式}
✅ 敏感配置 → 加密存储，禁止明文进 Git
✅ 变更流程 → 先 Beta 灰度 → 再全量；保留历史版本可回滚
✅ 多环境 → dev/test/prod 用 Namespace 隔离
```

## 37.8 练习与总结

### 练习题（动手实操）

```bash
# 1. 单机 Docker 启动 Nacos（第 15 章命令），用 curl 注册/注销一个临时实例并观察控制台
# 2. 创建一个持久实例（ephemeral=false），对比它与临时实例在健康检查上的差异（停掉后观察）
# 3. 建 dev/test 两个 Namespace，分别注册同名服务，验证互不可见（37.3.1）
# 4. 把某实例权重调为 0，观察流量不再分发到它（37.3.2）
# 5. 配置中心：新建 order-service-dev.yaml，用 @RefreshScope + @Value 动态刷新（37.4）
# 6. 给配置做一次"历史版本回滚"和"Beta 灰度发布"，观察效果（37.4.3）
# 7. 用 37.5 的 Compose 拉起三节点集群 + MySQL，验证任意节点挂掉后服务发现不受影响（37.5.5）
# 8. 开启鉴权（NACOS_AUTH_ENABLE=true），用旧账号密码验证 403，修改默认密码
```

### 面试高频问题清单

| 问题 | 答案要点 |
| --- | --- |
| Nacos 是什么？ | 注册中心 + 配置中心二合一 + 动态 DNS + 管理平台 |
| 服务发现为什么用 AP，配置为什么用 CP？ | 注册可容忍短暂不一致（本地缓存兜底）；配置必须强一致（不能新旧混跑） |
| Distro 协议是什么？ | Nacos 自研 AP 协议，临时实例注册表内存存储 + 节点间异步复制最终一致 |
| 临时实例与持久实例区别？ | 心跳 vs 主动探测；AP 的 Distro vs CP 的 Raft |
| 2.x 相对 1.x 架构变化？ | gRPC 长连接 + 主动推送；端口 9848/9849；百万级实例 |
| 配置动态刷新原理？ | 客户端长轮询（挂起 30s），有变更秒级返回；本地缓存 + MD5 比对；@RefreshScope 重建 Bean |
| Namespace / Group / Data ID 区别？ | 环境隔离 / 业务分组 / 配置标识 |
| 保护阈值是什么？ | 健康实例比例低于阈值时把不健康实例按比例兜底，防健康实例被打爆 |
| 优雅下线怎么做？ | 权重归零排空 → 主动 deregister → 等消费者感知 → 停止 |
| Nacos 集群怎么部署？ | ≥3 节点 + 外部 MySQL，MODE=cluster + NACOS_SERVERS |
| 客户端容灾？ | 本地缓存 + failover 文件；Server 全挂服务不立即中断 |
| Nacos 与 Eureka / Consul / ZK 对比？ | 见 37.1.2 表格 |

### 本章小结

- **定位**（37.1）：注册中心 + 配置中心二合一，1.x → 2.x 的 gRPC 长连接架构升级；
- **原理**（37.2）：AP（Distro）注册 + CP（Raft）配置的双模型设计，临时/持久实例与健康检查、服务发现全流程与容灾兜底；
- **注册中心治理**（37.3）：Namespace/Group/Service/Cluster 四级隔离、权重与灰度、保护阈值、优雅上下线；
- **配置中心**（37.4）：Data ID/Group/Namespace 模型、长轮询原理、历史回滚/灰度/导入导出、Java 刷新注意点；
- **生产部署**（37.5~37.7）：三节点集群 + MySQL、安全加固、高频排障与最佳实践；
- **工程集成**（37.6）：与第 15/30/32 章打通，覆盖"入门 → 治理 → 项目实战"完整闭环。

配套：第十五章（Nacos 入门）、第三十章（治理深度：优雅上下线、负载均衡策略）、第三十二章（项目实战：3 节点集群）、第三十五章（Docker 部署）、第二十三章（Zookeeper 对比）。

---

至此，第 37 章 Nacos 系统学习完成。返回：[README.md](./README.md)
