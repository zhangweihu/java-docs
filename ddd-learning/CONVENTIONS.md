# DDD 专题编写约定（CONVENTIONS）

> 本文件是 `ddd-learning/` 专题所有产物（71~80 章 markdown + `examples/` 下 4 个 Spring Boot 示例工程）必须严格遵循的**共享约定**。
> 任何违反本约定的代码、命名或文档，视为不合格。

---

## 一、技术栈（所有示例工程统一）

| 维度 | 选型 | 版本 | 备注 |
| --- | --- | --- | --- |
| 语言 | Java | 17 | 与 `mall-boot/` 一致 |
| 框架 | Spring Boot | 3.2.5 | JDK 17 基线 |
| ORM | MyBatis-Plus | 3.5.6 | 启动器 `mybatis-plus-spring-boot3-starter` |
| 数据库 | MySQL | 8.0 | H2 仅用于 `01-layered-comparison` 演示 |
| 缓存 | Spring Data Redis | 7.x | 仅 04 工程使用 |
| 消息 | Spring AMQP / RocketMQ Spring Boot Starter | RabbitMQ 3.x / RocketMQ 5.x | 仅 04 工程使用 |
| 工具 | Lombok | 1.18.x | 领域实体**禁用** `@Data` |
| 测试 | JUnit 5 + Spring Boot Test | 与 Boot 3.2 对应 | 领域层用纯 JUnit（不起容器） |
| 构建 | Maven | 3.8+ | 父 POM 复用 spring-boot-starter-parent |

> **强约束**：每个示例工程的 `pom.xml` 必须显式标注 `spring-boot-starter-parent 3.2.5`、`mybatis-plus 3.5.6`、`java.version 17`，否则视为不合格。

---

## 二、包名与目录结构约定

### 2.1 工程根包名

| 工程 | 根包名 |
| --- | --- |
| `examples/01-layered-comparison` | `com.ddd.layered` |
| `examples/02-modular-monolith` | `com.ddd.modular` |
| `examples/03-ecommerce-order` | `com.ddd.ecommerce.order` |
| `examples/04-saga-payment` | `com.ddd.saga` |

### 2.2 标准四层包结构（所有示例工程统一）

```
com.ddd.<topic>/
├── domain/                       # 领域层（纯 Java，不依赖 Spring/MyBatis）
│   ├── model/                    # 实体、值对象、聚合、领域事件
│   ├── repository/               # 仓储接口（领域层定义）
│   └── service/                  # 领域服务（跨对象规则）
├── application/                  # 应用层（用例编排）
│   ├── service/                  # 应用服务（AppService）
│   ├── command/                  # 命令对象（CQRS 写侧入参）
│   └── query/                    # 查询对象（CQRS 读侧入参）
├── infrastructure/               # 基础设施层
│   ├── persistence/              # PO、Assembler、MyBatis Mapper、仓储实现
│   ├── messaging/                # 消息发布/订阅实现
│   └── external/                 # 远程/外部系统防腐层实现
├── interfaces/                   # 接口层
│   ├── web/                      # REST Controller
│   └── dto/                      # 请求/响应 DTO
└── DddXxxApplication.java        # Spring Boot 主启动类
```

### 2.3 多模块单体（仅 02 工程）的特殊包名

每个 Maven 子模块独立根包：`com.ddd.modular.{user|order|inventory|payment}.{domain|application|infrastructure|interfaces}`，**禁止跨模块直接 import 对方 internal 包**。

---

## 三、命名约定

| 类型 | 规则 | 示例 |
| --- | --- | --- |
| 类名 | 大驼峰 | `Order`, `OrderItem`, `Money` |
| 抽象/基类 | `Abstract*` 或 `Base*` | `AbstractAggregateRoot` |
| 接口 | 名词或形容词 | `OrderRepository`, `Payable` |
| 实现类 | `Impl` 或技术前缀 | `MybatisOrderRepository`, `OrderServiceImpl` |
| 方法名 | 小驼峰 | `confirmPayment()`, `recalcTotal()` |
| 包名 | 全小写、点分隔 | `com.ddd.ecommerce.order.domain.model` |
| 常量 | 全大写下划线 | `MAX_RETRY_TIMES` |
| 布尔字段 | `is*` / `can*` / `should*` | `isPaid`, `canCancel` |
| 标识值对象 | `*Id` | `OrderId`, `CustomerId` |
| 领域事件 | 过去式（已发生） | `OrderPaidEvent`, `InventoryReservedEvent` |
| 命令 | 祈使句/动词原形 | `SubmitOrderCommand`, `CancelOrderCommand` |
| 状态枚举 | 名词 | `OrderStatus.PENDING` |
| Mapper XML | 与 Mapper 同名 | `OrderMapper.java` ↔ `OrderMapper.xml` |
| 表名 | `t_` 前缀 + snake_case | `t_order`, `t_order_item` |
| 字段名（PO） | snake_case | `order_no`, `created_at` |

---

## 四、领域建模规范（与第 40~46 章一致）

### 4.1 实体（Entity）

```java
// ❌ 禁用
@Data
public class Order {
    private Long id;
    private Integer status;
}

// ✅ 推荐
public class Order {
    private final OrderId id;
    private OrderStatus status;
    private final List<OrderItem> items = new ArrayList<>();

    public Order(OrderId id, CustomerId customerId) {
        this.id = id;
        this.customerId = customerId;
        this.status = OrderStatus.PENDING;
    }

    public void pay() {
        enforceStatus(OrderStatus.SUBMITTED, "支付");
        this.status = OrderStatus.PAID;
        registerEvent(new OrderPaidEvent(this.id, Instant.now()));
    }

    private void enforceStatus(OrderStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                "订单当前状态" + status + "不允许" + action);
        }
    }
}
```

### 4.2 值对象（Value Object）

- `final` 类 + `final` 字段；
- 无 setter，修改返回新对象；
- `equals/hashCode` 全字段比较；
- Java 17 可用 `record` 实现。

### 4.3 聚合（Aggregate）

- 聚合根命名 `Xxx`（不带 `Aggregate` 后缀），内部实体 `static class` 隐藏可见性；
- 对外只暴露聚合根引用，内部集合返回 `List.copyOf` 或 `Collections.unmodifiableList`；
- 跨聚合只存 `*Id`，不持对方对象。

### 4.4 仓储（Repository）

```java
// 领域层定义接口
public interface OrderRepository {
    Order findById(OrderId id);
    void save(Order order);
    Optional<Order> findByOrderNo(String orderNo);
}

// 基础设施层实现
@Repository
public class MybatisOrderRepository implements OrderRepository {
    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    // ... 组装 Order PO ↔ 领域对象
}
```

### 4.5 命名规则强制清单

- ✅ 命令 = 祈使句 → `SubmitOrderCommand`
- ✅ 事件 = 过去式 → `OrderSubmittedEvent` / `OrderPaidEvent`
- ✅ 聚合内不变量校验写在聚合根方法内（充血模型）
- ❌ 实体上禁止 `@Data`（破坏封装）
- ❌ Service 注入 `*Mapper`（应用服务/领域服务只注入 `*Repository`）
- ❌ 实体方法内调用远程 / 事务注解（事务归应用服务）

---

## 五、文档（markdown）章节约定

### 5.1 文件命名

`java-learning/7X-章节标题.md`（与 70 章前保持完全一致：`XX-标题.md`，无空格）。

### 5.2 章首模板（每章前 12 行）

```markdown
# 第X章 中文标题

> 本章目标：……
>
> 前置知识：第Y章……、第Z章……

## X.1 小节标题
```

### 5.3 章尾模板

```markdown
### 本章小结

- 要点 1
- 要点 2

配套：第A章、第B章
---
至此，第X章 … 学习完成。下一章 [7Y-...](./7Y-...) ｜ 返回：[README.md](./README.md)
```

### 5.4 内容硬要求

| 项 | 要求 |
| --- | --- |
| 行数 | 800~1200 行（与 40-46/70 章风格一致） |
| 代码块 | 所有示例必须有可编译的完整代码块（import + package + 类签名） |
| 对照表 | 涉及对比的概念必须有 markdown 表格 |
| 实战节 | 每章必须含"实战"或"案例"小节，给出端到端代码片段 |
| 面试题 | 章尾"面试高频问题清单"≥8 条 |
| 练习题 | 章尾"练习题"≥5 条 |
| 决策树 | 凡涉及选型必须给出 markdown 决策树（`├── 是/否` 风格） |
| 提示框 | 关键结论用 `> **一句话**：…` 或 `> 重要：` 引出 |
| 衔接 | 章首"前置知识"、章尾"配套"必须明确指向具体章节号 |

### 5.5 严禁项

- ❌ 占位符如 `// ...`、`// 省略`、`// TODO`
- ❌ 伪代码（不可编译的"骨架"）
- ❌ 与 40-46 章已有内容 100% 重复（必须深化或新角度）
- ❌ Markdown 渲染异常（如裸 `<` `>` 未转义）

---

## 六、示例工程交付清单（每个工程必含）

```
examples/XX-<name>/
├── pom.xml                        # Maven 配置（Boot 3.2.5 + Java 17）
├── README.md                      # 工程说明、运行步骤、技术栈
├── docker-compose.yml             # （03/04 必须）MySQL+Redis+应用一键编排
├── Dockerfile                     # （03/04 推荐）多阶段构建
├── src/main/java/com/ddd/<topic>/ # 源码（按第二节四层包结构）
├── src/main/resources/
│   ├── application.yml            # 主配置
│   ├── application-dev.yml        # 开发环境（推荐）
│   └── mapper/                    # MyBatis XML（如使用）
├── src/test/java/                  # 单元测试
└── sql/                            # 建表 SQL（03/04 必须，01/02 可选）
    └── schema.sql
```

**最低代码文件数量（每个工程）**：
- 领域层：≥4 个文件（实体/值对象/聚合/仓储接口）
- 应用层：≥2 个文件（应用服务 + 命令/查询对象）
- 基础设施层：≥3 个文件（PO + Mapper + 仓储实现）
- 接口层：≥2 个文件（Controller + DTO）
- 测试：≥3 个测试类（领域层纯内存测试为必须项）

---

## 七、可运行性基线

- ✅ `pom.xml` 可被 Maven 解析，无缺失依赖
- ✅ 主类有标准 `main` 方法 + `@SpringBootApplication`
- ✅ `application.yml` 数据源/Redis 默认端口（3306/6379）可通过环境变量覆盖
- ✅ 01 工程用 H2 内存数据库，可直接 `mvn spring-boot:run` 启动
- ✅ 03/04 工程提供 `docker-compose.yml`，包含 MySQL、Redis、应用三件套
- ⚠️ 不要求"实机编译验证通过"——但所有代码必须语法正确、import 路径正确、注解配置无矛盾

---

## 八、与其他专题的区分

| 主题 | 归属 | 不应在本专题出现 |
| --- | --- | --- |
| 基础语法 | 第 1-7 章 | 本专题不重复 Java 基础 |
| 设计模式 | 第 14 章 | 仅在分层应用时引用，不展开 |
| Spring Boot 入门 | 第 9 章 | 直接默认读者会用 |
| 状态机基础 | 第 42 章 | 直接调用，不重复讲解 |
| CQRS/ES 基础 | 第 43 章 | 直接调用，本专题聚焦"如何在分层架构中组织 CQRS" |
| 事件风暴方法 | 第 44 章 | 直接调用 |
| Axon 框架 | 第 45 章 | 直接调用 |
| 分布式事务基础 | 第 46 章 | 直接调用 |

**本专题的独特价值**：**把 40-46/70 章的"模式"串成"分层架构下的工程实现"**，补充 40-46 章未深入的整洁架构、六边形、Modular Monolith、多领域集成、可观测演进。