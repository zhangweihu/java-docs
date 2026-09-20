# 第七十五章 Modular Monolith（模块化单体落地）

> **本章目标**：在 Spring Boot 单进程内实现"模块化单体"——Maven 多模块 + 强边界 + 各自领域 + 未来可拆微服务。
>
> **前置知识**：第 74 章（应用服务与 CQRS 入口）、第 41 章（聚合/限界上下文）、第 32 章（mall-cloud 微服务）。

> 一句话：**模块化单体 = 单进程内的"准微服务"——既有微服务的边界，又有单体的部署效率**。

---

## 75.1 为什么先模块化单体，再拆微服务

Sam Newman 在《Building Microservices》第 2 版反复强调：

> **"Start with a monolith, not microservices."**

直接拆微服务的代价：

| 代价 | 表现 |
| --- | --- |
| 分布式复杂度 | 跨服务调用、网络分区、最终一致、Saga 补偿 |
| 部署爆炸 | N 个服务 = N 倍 CI/CD、监控、日志、告警 |
| 调试困难 | 一个请求跨 5 个服务，日志关联复杂 |
| 团队规模要求 | 至少 10+ 人才能形成 1 服务 1 团队的边界 |
| 早期方向调整 | 业务边界识别错就全盘重构 |

模块化单体用"单进程 + 物理隔离"换来**低成本享受微服务的部分好处**。

---

## 75.2 Maven 多模块结构

```
modular-monolith/
├── pom.xml                                  ← 父 POM（packaging=pom）
├── modular-common/                          ← 共享：DTO、异常、Util
│   ├── pom.xml
│   └── src/main/java/com/ddd/modular/common/
│       ├── dto/PageResult.java
│       └── exception/BusinessException.java
├── modular-user/                            ← 用户上下文
│   ├── pom.xml
│   └── src/main/java/com/ddd/modular/user/
│       ├── domain/{model,repository}
│       ├── application/api/                 ← 对外暴露
│       └── infrastructure/persistence/
├── modular-order/                           ← 订单上下文
├── modular-inventory/                        ← 库存上下文
├── modular-payment/                         ← 支付上下文
├── modular-app/                             ← Spring Boot 启动模块
└── modular-archunit/                        ← 边界守护测试
```

### 75.2.1 父 POM 关键配置

```xml
<project>
    <groupId>com.ddd</groupId>
    <artifactId>modular-monolith</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <modules>
        <module>modular-common</module>
        <module>modular-user</module>
        <module>modular-order</module>
        <module>modular-inventory</module>
        <module>modular-payment</module>
        <module>modular-app</module>
        <module>modular-archunit</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.ddd</groupId>
                <artifactId>modular-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <!-- 业务模块互相不直接 dependencyManagement -->
        </dependencies>
    </dependencyManagement>
</project>
```

### 75.2.2 业务模块 POM

```xml
<!-- modular-order/pom.xml -->
<project>
    <artifactId>modular-order</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.ddd</groupId>
            <artifactId>modular-common</artifactId>
        </dependency>
        <!-- 严格禁止依赖其他业务模块的 internal 包 -->
    </dependencies>
</project>
```

---

## 75.3 模块边界：包名 + Maven 依赖双向约束

### 75.3.1 包名规范

| 模块 | 包前缀 |
| --- | --- |
| modular-common | `com.ddd.modular.common.*` |
| modular-user | `com.ddd.modular.user.{domain,application,infrastructure}.*` |
| modular-order | `com.ddd.modular.order.*` |
| modular-inventory | `com.ddd.modular.inventory.*` |
| modular-payment | `com.ddd.modular.payment.*` |

### 75.3.2 严格隔离 internal 包

```
modular-order/
└── com/ddd/modular/order/
    ├── domain/                                ← internal，外部禁止
    ├── application/
    │   ├── api/OrderQueryService.java         ← 对外暴露（其他模块可读）
    │   └── service/OrderAppService.java       ← internal
    ├── infrastructure/
    │   └── persistence/MybatisOrderRepository.java   ← internal
    └── interfaces/
        └── web/OrderController.java           ← Spring MVC 入口（仅 modular-app 使用）
```

**约束**：

- `domain` 与 `infrastructure` 绝不对外暴露。
- `application.api` 是其他模块唯一可依赖的包。
- 跨模块调用只能通过 `application.api` 接口 + Spring Bean 注入。

---

## 75.4 模块间通信：三种模式

### 75.4.1 直接调用（同进程应用服务）

```java
// modular-order 的 OrderAppService
@Service
public class OrderAppService {
    private final OrderRepository orderRepository;
    private final UserQueryService userQueryService;   // modular-user 的 application.api

    public OrderId submit(SubmitOrderCommand cmd) {
        UserView user = userQueryService.findById(cmd.customerId())
            .orElseThrow(() -> new UserNotFoundException(cmd.customerId()));
        // ...
    }
}
```

**优点**：简单、低延迟（方法调用）。

**缺点**：模块间耦合（订单上下文知道用户上下文的应用服务接口）。

### 75.4.2 进程内事件总线

```java
// 订单发布事件
@Service
public class OrderAppService {
    private final ApplicationEventPublisher events;
    @Transactional
    public OrderId submit(SubmitOrderCommand cmd) {
        Order order = Order.create(/* ... */);
        repository.save(order);
        events.publishEvent(new OrderCreatedEvent(order.id()));
        return order.id();
    }
}

// 库存订阅
@Component
public class InventoryOrderEventListener {
    private final InventoryRepository inventoryRepository;
    @Transactional
    @EventListener
    public void on(OrderCreatedEvent event) {
        // 预留库存
    }
}
```

**优点**：模块间不直接依赖（解耦）。

**缺点**：进程内事件总线，跨进程不可用。

### 75.4.3 跨模块接口适配器（防腐层）

```java
// modular-order 中定义端口
public interface InventoryPort {
    void reserve(InventoryReserveCommand cmd);
}

// modular-inventory 中实现端口
@Service
public class InventoryAdapter implements InventoryPort {
    private final InventoryRepository repository;
    @Override
    @Transactional
    public void reserve(InventoryReserveCommand cmd) {
        Inventory inv = repository.findById(cmd.productId()).orElseThrow();
        inv.reserve(cmd.quantity());
        repository.save(inv);
    }
}
```

**适用**：跨模块业务语义明确，调用方需要"定制接口"。

---

## 75.5 实战：模块化单体工程

### 75.5.1 业务模块四层包结构（以 modular-order 为例）

```java
// domain 层
package com.ddd.modular.order.domain.model;

public class Order {
    private final OrderId id;
    private OrderStatus status;
    private final List<OrderItem> items;

    public Order(OrderId id, List<OrderItem> items) { /* 不变量校验 */ }
    public void pay() { /* 状态机校验 */ }
    public Money total() { /* 业务计算 */ }
}

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(OrderId id);
}

// application 层
package com.ddd.modular.order.application.service;

@Service
public class OrderAppService {
    private final OrderRepository repository;
    private final ApplicationEventPublisher events;

    @Transactional
    public OrderId submit(SubmitOrderCommand cmd) { /* 编排 */ }
    public void pay(PayOrderCommand cmd) { /* 编排 */ }
}

// application.api 层（对外暴露）
package com.ddd.modular.order.application.api;

public interface OrderQueryService {
    Optional<OrderView> findById(Long orderId);
    Page<OrderView> search(FindOrderQuery query);
}

// infrastructure 层
package com.ddd.modular.order.infrastructure.persistence;

@Repository
public class MybatisOrderRepository implements OrderRepository {
    // MyBatis 实现
}
```

### 75.5.2 modular-app 启动模块

```java
@SpringBootApplication
@ComponentScan(basePackages = "com.ddd.modular")    // 扫描所有业务模块
public class ModularApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModularApplication.class, args);
    }
}
```

`application.yml`：

```yaml
spring:
  application:
    name: modular-monolith
  datasource:
    url: jdbc:mysql://localhost:3306/ddd_modular
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate

server:
  port: 8080

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

---

## 75.6 ArchUnit 边界守护

### 75.6.1 通用规则

```java
class ModularBoundaryRulesTest {

    private static final JavaClasses classes = new ClassFileImporter()
        .importPackages("com.ddd.modular");

    @Test
    void business_modules_should_not_depend_on_each_others_infrastructure() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ddd.modular.order..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.ddd.modular.inventory.infrastructure..");

        rule.check(classes);
    }

    @Test
    void domain_should_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");
        rule.check(classes);
    }

    @Test
    void domain_should_not_depend_on_application() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..application..");
        rule.check(classes);
    }

    @Test
    void controllers_should_not_depend_on_repositories_directly() {
        ArchRule rule = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository");
        rule.check(classes);
    }

    @Test
    void repositories_should_be_interfaces_in_domain() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().beInterfaces();
        rule.check(classes);
    }
}
```

### 75.6.2 模块边界规则

```java
@Test
void user_should_only_be_accessed_via_application_api() {
    ArchRule rule = noClasses()
        .that().resideOutsideOfPackage("com.ddd.modular.user..")
        .should().dependOnClassesThat()
        .resideInAPackage("com.ddd.modular.user.domain..");

    rule.check(classes);
}

@Test
void app_module_should_be_only_entry_point() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("com.ddd.modular.app..")
        .should().dependOnClassesThat()
        .resideInAPackage("com.ddd.modular.*.infrastructure..");

    // app 模块可以引用所有 business 模块，但其他模块不能反向依赖 app
}
```

### 75.6.3 接入 CI

把 ArchUnit 测试集成到 CI：

```yaml
# .github/workflows/ci.yml
- name: ArchUnit Tests
  run: ./mvnw test -pl modular-archunit
```

任何提交违反规则的代码，CI 红灯阻断合入。

---

## 75.7 模块化单体的部署形态

### 75.7.1 单体部署（默认）

```bash
mvn -pl modular-app -am clean package
java -jar modular-app/target/modular-app-1.0.0.jar
```

启动一个 Spring Boot 进程，所有业务模块同时运行。

### 75.7.2 单体多 Profile 部署

```bash
java -jar modular-app.jar --spring.profiles.active=order-inventory
# 只激活订单 + 库存，禁用用户/支付（用于灰度或压测）
```

配合 `@Profile` 注解可实现"按业务模块启停"。

---

## 75.8 模块化单体 → 微服务的平滑迁移

### 75.8.1 拆分触发条件

| 触发条件 | 拆法 |
| --- | --- |
| 单一模块需要独立部署频率 | 拆为独立 Spring Boot 服务 |
| 单一模块需要独立伸缩 | 配合 K8s HPA 独立伸缩 |
| 团队按业务线拆分 | 一业务线一仓库一服务 |
| 跨团队协作冲突 | 物理隔离解决政治问题 |

### 75.8.2 拆分步骤

```
模块化单体                          微服务
modular-user    ────拆分───►  user-service
                                （独立仓库 + 独立 Spring Boot 进程）
modular-order   ────拆分───►  order-service
modular-inventory ──拆分───►  inventory-service
modular-payment ────拆分───►  payment-service

modular-app  ────调整───►   api-gateway（聚合多个微服务入口）
```

具体步骤：

1. **抽取 application.api**：把模块的对外接口（`OrderQueryService`）独立打包为 `order-api.jar`，提供给其他模块。
2. **替换模块间通信**：原本同进程调用改为 Feign/HTTP。
3. **独立数据库**：每个服务独立数据库（schema 物理隔离）。
4. **Saga 替代本地事务**：跨服务用 Saga 协调（详见第 79 章）。
5. **基础设施独立**：每个服务独立 CI/CD、独立监控。

### 75.8.3 不应拆的迹象

| 迹象 | 表现 |
| --- | --- |
| 模块边界识别不清 | 改一个用例需要改 3 个模块 |
| 业务还在快速迭代 | 微服务会让每次迭代变慢 5 倍 |
| 团队规模 < 10 人 | 沟通成本远小于分布式成本 |
| 数据强一致性要求 | 跨库事务成本高于集中部署 |

---

## 75.9 模块化单体的代价

| 代价 | 缓解 |
| --- | --- |
| Maven 构建时间增长（多模块编译） | 用 `-am -pl` 增量构建 |
| 启动时间增加（多个 Spring Bean） | 用 `--spring.profiles.active` 按需激活 |
| 包结构复杂（新人不友好） | 用 ArchUnit 守护 + README 强制 |
| 进程内事件总线跨进程失效 | 提前设计"可切换为 MQ"的事件接口 |

---

## 75.10 模块化单体 vs 微服务：选型决策树

```
你的项目当前状态？
│
├── 初创/原型/工具型项目
│   └── 单体即可（不需模块化）
│
├── 中型项目（5~10 人）
│   ├── 业务边界清晰 ──► 模块化单体 ✅
│   └── 业务边界模糊 ──► 先单体（演进清晰后再拆模块）
│
├── 大型企业项目（10~50 人）
│   ├── 单团队 ──► 模块化单体
│   ├── 多团队但同一业务 ──► 模块化单体 + 模块所有权划分
│   └── 多团队多业务 ──► 微服务（按业务线）
│
└── 超大规模（50+ 人）
    └── 微服务 + 模块化单体（每个微服务内部仍按模块化单体规范）
```

---

### 本章小结

- 模块化单体 = 单进程 + Maven 多模块 + 严格边界。
- 包名 + Maven 依赖双向约束是物理层面的边界手段。
- ArchUnit 把"架构约束"变成可执行测试，集成到 CI。
- 模块化单体 → 微服务是平滑迁移（先抽 api，再换通信方式）。
- 启动触发条件：单一模块独立部署频率 / 独立伸缩 / 团队拆分。

### 面试高频问题清单（75 章）

1. 什么是模块化单体？与微服务、单体的区别？
2. Maven 多模块的依赖关系如何约束？
3. 为什么模块间只能通过 application.api 通信？
4. ArchUnit 的核心价值是什么？
5. 模块化单体的包结构规范是什么？
6. 何时应该拆微服务？列出 3 个触发条件。
7. 拆微服务的步骤是什么？
8. 模块化单体的 3 个主要代价是什么？

### 练习题

1. 把现有的单 Maven 模块单体拆为 4 模块（user/order/inventory/payment）。
2. 用 ArchUnit 写 5 条边界守护规则并接入 CI。
3. 设计 OrderQueryService 作为对外 API，分析"哪些方法应暴露，哪些不应"。
4. 思考：你们的项目如果拆微服务，应该先拆哪个模块？为什么？
5. 列出 3 个"模块边界识别错误"的反模式，逐一给出修复方案。

### 配套

- **前置**：第 74 章（应用服务与 CQRS 入口）、第 41 章（聚合/限界上下文）
- **后续**：第 76 章（订单领域实战）、第 77 章（库存领域实战）、第 78 章（支付领域实战）

---
至此，第七十五章 Modular Monolith 落地 学习完成。下一章 [76-订单领域实战.md](./76-订单领域实战.md) ｜ 返回：[README.md](./README.md)