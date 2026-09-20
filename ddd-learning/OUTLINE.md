# DDD 专题章节大纲（OUTLINE）

> 本文件是 `ddd-learning/` 专题的章节大纲与示例工程作业指导。71~80 章共 10 章，每章 800~1200 行 markdown，对应示例工程分布在 `examples/01~04/` 下。写作时请严格遵循 `CONVENTIONS.md`。

---

## 全景（章节定位）

```
71 章  分层架构全景        ┐
72 章  整洁架构             │  理论层（5 章）
73 章  六边形架构           │  + examples/01-layered-comparison
74 章  应用服务与 CQRS 入口 ┘  + examples/02-modular-monolith
75 章  Modular Monolith
 ────────────────────
76 章  订单领域实战        ┐
77 章  库存领域实战        │  领域层（3 章）
78 章  支付领域实战        ┘  + examples/03-ecommerce-order
 ────────────────────
79 章  跨上下文集成（Saga / Outbox / 可观测）→ examples/04-saga-payment
80 章  专题收官（演进路线 + 面试冲刺）
```

---

## 第七十一章 分层架构全景（MVC → 三层 → DDD → 整洁 → 六边形 → 模块化单体）

> **目标**：建立"分层架构"的全景认知，理解 6 种主流分层方案的演进逻辑、各自解决的痛点与适用场景，能为自己的项目选对分层。
> **前置**：第 40 章（充血模型）、第 41 章（DDD 战术）、第 14 章（设计模式）、第 28 章（SOLID）。

### 必含小节

- **71.1** "分层" 的本质：把变化频率不同的层切开
- **71.2** MVC 模式（Model-View-Controller）：Web 时代的开山鼻祖
- **71.3** 三层架构（Presentation / Business / Data Access）：企业 Java 主流
- **71.4** DDD 四层（User Interface / Application / Domain / Infrastructure）：战略落地的标准
- **71.5** 整洁架构（Clean Architecture / Hexagonal）：同心圆与依赖规则
- **71.6** 六边形架构（Ports & Adapters）：Inbound/Outbound 的工程表达
- **71.7** 模块化单体（Modular Monolith）：微服务之前的最后一道
- **71.8** 六种架构对比表（耦合度/可演进性/团队协作/适用场景）
- **71.9** 实战：同一业务（订单）在六种分层下的代码骨架对照
- **71.10** 选型决策树（按团队规模/业务复杂度/演进阶段）

### 关键产出

- 六种分层方案的核心结构图（ASCII）
- 一张主对比表（≥10 个维度）
- 选型决策树（至少 4 个分支条件）

---

## 第七十二章 整洁架构（Clean Architecture 落地）

> **目标**：把 Robert C. Martin 的 Clean Architecture 落到 Spring Boot 工程上，掌握"同心圆 + 依赖规则 + 跨边界适配器"三大支柱。
> **前置**：第 71 章、第 41 章（仓储接口倒置）、第 28 章（依赖倒置）。

### 必含小节

- **72.1** 整洁架构思想：同心圆与依赖方向
- **72.2** 四层与同心圆的对应：Entities / Use Cases / Interface Adapters / Frameworks
- **72.3** 依赖规则：源代码依赖只能由外向内
- **72.4** 跨边界数据传递：DTO 边界、避免"内部实体穿透"
- **72.5** 适配器（Adapter）的角色：Inbound / Outbound
- **72.6** 实战：整洁架构的 Spring Boot 工程结构
- **72.7** 与传统三层的差异对比表
- **72.8** 整洁架构的边界守护：Maven 模块隔离、ArchUnit

### 关键产出

- 整洁架构的 Spring Boot 包结构 + 模块化方案
- 跨边界数据转换代码示例（Order ↔ OrderResponseDTO）
- ArchUnit 守护规则示例代码

---

## 第七十三章 六边形架构（Ports & Adapters）

> **目标**：掌握六边形架构（端口与适配器）的工程落法，把"业务核心不依赖任何外部技术"的理念落到 Spring Boot。
> **前置**：第 72 章、第 41 章（仓储/防腐层）。

### 必含小节

- **73.1** 六边形起源：Alistair Cockburn 的动机
- **73.2** Inbound Port（驱动端口） vs Outbound Port（被驱动端口）
- **73.3** 主适配器（Driving Adapter） vs 从适配器（Driven Adapter）
- **73.4** 与整洁架构的关系：六边形是整洁架构的简化表达
- **73.5** 实战：订单上下文的六边形实现
  - 入口：REST Controller（主适配器） → Inbound Port（UseCase） → 领域
  - 出口：领域 → Outbound Port（Repository） → MyBatis 适配器（从适配器）
- **73.6** 多入口适配器：REST / CLI / 消息 共享同一 UseCase
- **73.7** 多出口适配器：MySQL / Redis / 远程 Feign 共享同一 Repository Port
- **73.8** 端口契约设计：参数/返回类型的"边界友好"原则

### 关键产出

- 六边形架构图（核心六边形 + 周边适配器）
- 一个完整的"订单下单"用例：3 种入口适配器（REST/CLI/MQ）+ 2 种出口适配器（DB/Mock）
- 六边形 vs 三层 vs 整洁的最终对照表

---

## 第七十四章 应用服务与 CQRS 入口（UseCase 编排）

> **目标**：把"应用服务（Application Service）"作为分层架构的"用例编排入口"做透，掌握 Command / Query 分离、事务边界、跨聚合事件发布。
> **前置**：第 73 章、第 41 章（聚合/仓储/领域服务）、第 43 章（CQRS）、第 46 章（Saga）。

### 必含小节

- **74.1** 应用服务在 DDD 中的位置：薄薄的"胶水层"
- **74.2** UseCase 风格 vs Service 风格：方法名直接是动词（`submitOrder` / `cancelOrder`）
- **74.3** 命令对象（Command）与查询对象（Query）的设计规范
- **74.4** 事务边界：应用服务方法 = 一个事务单元
- **74.5** 跨聚合协调：本地一致性 / 最终一致性 / Saga 三种选择
- **74.6** 领域事件发布：同步发布 vs Outbox 模式
- **74.7** 实战：订单应用服务的 5 个核心用例
- **74.9** 防腐层（ACL）在应用服务中的落位

### 关键产出

- `OrderAppService` 完整代码（5 个用例 + 命令对象）
- 应用服务 / 领域服务 / 仓储的依赖关系图
- 事务边界决策树

---

## 第七十五章 Modular Monolith（模块化单体落地）

> **目标**：在 Spring Boot 单进程内实现"模块化单体"——Maven 多模块 + 强边界 + 各自领域 + 未来可拆微服务。
> **前置**：第 74 章、第 41 章（聚合/限界上下文）、第 32 章（mall-cloud 微服务）。

### 必含小节

- **75.1** 为什么先模块化单体，再拆微服务
- **75.2** Maven 多模块结构：父 POM + 领域模块 + 应用模块 + 基础设施模块
- **75.3** 模块边界：包名隔离 + Maven 依赖单向
- **75.4** 模块间通信：直接调用 / 事件总线 / 防腐层
- **75.5** 实战：模块化单体工程（user / order / inventory / payment 四模块）
- **75.6** ArchUnit 守护：禁止跨模块反向依赖、禁止 internal 包外泄
- **75.7** 模块化单体的部署形态
- **75.8** 模块化单体 → 微服务的平滑迁移路径

### 关键产出

- 完整 Maven 多模块目录树（pom 引用关系）
- 模块依赖规则图
- ArchUnit 守护测试代码
- 与 `mall-cloud/` 微服务方案的对照（演进路径）

---

## 第七十六章 订单领域实战（聚合 + 状态机 + 领域事件 + ACL）

> **目标**：以订单领域为载体，把第 40~46 章的 DDD 战术要素在分层架构下"工程化"——完整实现一个生产级订单上下文。
> **前置**：第 73~75 章、第 41/42/44 章。

### 必含小节

- **76.1** 订单领域的限界上下文边界
- **76.2** 通用语言：业务专家对话 → 代码命名
- **76.3** 订单聚合（含 OrderItem）充血实现
- **76.4** Money 值对象（Java 17 record）+ 库存快照值对象
- **76.5** 仓储接口倒置 + MyBatis 仓储实现（PO/Assembler/Mapper）
- **76.6** 状态机：PENDING → PAID → SHIPPED → COMPLETED + 超时关单
- **76.7** 领域事件：OrderCreated / OrderPaid / OrderCancelled
- **76.8** 应用服务 5 个用例：创建/支付/取消/退款/查询
- **76.9** 防腐层：调用用户/商品上下文的接口适配器
- **76.10** 单元测试：领域层纯内存测试 5+ 个

### 关键产出

- 完整订单上下文代码（`com.ddd.ecommerce.order.*`）
- 5 个核心用例的端到端调用链
- 状态机配置 + 守卫 + 动作
- 防腐层（ACL）实现

---

## 第七十七章 库存领域实战（预留/释放/防超卖）

> **目标**：以库存领域为载体，掌握"高并发写入"的领域建模——预留 / 扣减 / 释放三态模型 + 防超卖 + 与订单的最终一致。
> **前置**：第 76 章、第 31 章（防超卖）、第 43 章（最终一致）。

### 必含小节

- **77.1** 库存领域边界：哪些逻辑属于库存？哪些属于订单？
- **77.2** 库存三态模型：可用 / 预留 / 已扣
- **77.3** Inventory 聚合：预留 / 扣减 / 释放行为
- **77.4** 防超卖：领域级原子操作 + 仓储 SQL 双重防线
- **77.5** 库存领域事件：InventoryReserved / InventoryReleased / InventoryCommitted
- **77.6** 与订单的最终一致：领域事件订阅 + 异步处理
- **77.7** 实战：库存应用服务 4 个用例（reserve / commit / deduct / release）
- **77.8** 单元测试：边界场景（库存不足/并发预留/超时释放）

### 关键产出

- Inventory 聚合充血实现
- 4 个核心用例代码
- 库存三态表结构设计（`t_inventory` / `t_inventory_reserve`）
- 防超卖 SQL 与领域校验对照

---

## 第七十八章 支付领域实战（账户聚合 / 金额 / Saga 入口）

> **目标**：以支付领域为载体，掌握"资金类"高一致业务在 DDD 中的建模——账户聚合、金额操作幂等、Saga 启动入口。
> **前置**：第 76~77 章、第 43 章、第 46 章（Saga）。

### 必含小节

- **78.1** 支付领域的边界与"资金不变量"
- **78.2** 账户聚合（Account）：余额 + 状态 + 历史
- **78.3** 资金操作幂等：基于幂等键（Idempotency Key）的设计
- **78.4** Saga 入口：PaymentAppService.startPayment() 启动 Saga
- **78.5** 转账聚合（Transfer）：发起方 + 接收方 + 中间态
- **78.6** 实战：账户 + 转账完整实现
- **78.7** 与订单的协作：Saga 步骤 1「冻结金额」+ 步骤 2「确认扣款」
- **78.8** 单元测试：幂等性测试 + 余额不足测试

### 关键产出

- Account 聚合 + Transfer 聚合充血实现
- 幂等键机制（`idempotency_key` 唯一约束）
- PaymentAppService.startPayment 启动 Saga 的代码

---

## 第七十九章 跨上下文集成（Saga / Outbox / 可观测）

> **目标**：把订单 / 库存 / 支付三个上下文在分层架构下集成，掌握 Saga 协调、Outbox 模式、可观测性设计。
> **前置**：第 76~78 章、第 46 章（Saga）、第 43 章（Outbox）、第 48 章（可观测）。

### 必含小节

- **79.1** 跨上下文集成的三种范式：直接调用 / 事件 / Saga
- **79.2** 上下文映射：防腐层（ACL）在集成中的位置
- **79.3** 事件总线：进程内（ApplicationEventPublisher）/ RabbitMQ / RocketMQ
- **79.4** Outbox 模式：业务事务 + 事件表 + 后台投递
- **79.5** Saga 编排式 / 协同式 在分层架构下的实现
- **79.6** 实战：下单 → 库存预留 → 支付扣款的 Saga 全流程
- **79.7** 可观测：Saga 日志、补偿日志、死信处理
- **79.8** 单元 + 集成测试：Mock 外部 / In-Memory 总线

### 关键产出

- 完整的 Saga 协调者（编排式）实现
- Outbox 模式核心代码（`outbox_event` 表 + 定时扫描）
- RabbitMQ 集成配置（按需）
- Saga 死信处理代码

---

## 第八十章 专题收官（演进路线 / 边界守护 / 面试冲刺）

> **目标**：把 71~79 章的核心结论浓缩为"演进路线图 + 边界守护手段 + 面试冲刺清单"。
> **前置**：71~79 章全部。

### 必含小节

- **80.1** 全景回顾：分层架构 × DDD × CQRS × Saga 矩阵图
- **80.2** 演进路线：单体 → 模块化单体 → 微服务 → 云原生（按需展开）
- **80.3** 边界守护手段矩阵：Maven 模块 / ArchUnit / 包扫描 / 静态检查
- **80.4** 团队协作：Conway 定律 × 模块边界 × 团队拓扑
- **80.5** 常见误区（10 条）：过度 DDD / 假 DDD / 微服务焦虑 / 事务滥用 / 状态机滥用 …
- **80.6** 面试冲刺：30 道分层+DDD 综合题（精选 71~79 章高频题）
- **80.7** 学习路径推荐：80 章后续该读什么？
- **80.8** 致读者与专题结语

### 关键产出

- 全景矩阵图（横向：分层方案 × 纵向：DDD 战术/战略/集成）
- 演进路线决策树（按业务规模 / 团队规模 / 复杂度）
- 30 道面试题清单 + 标准答案提纲

---

## 示例工程作业指导

### examples/01-layered-comparison（71-72 章配套）

**目标**：用同一业务（订单的"创建 + 支付"）演示六种分层架构的代码差异。

**核心演示**：
- 同业务在 6 种分层下的 Controller / Service / Repository / Domain 的差异
- 一键切换"分层模式"（通过 `@Profile` 或 Spring Bean 切换）

**最低文件清单**：
```
examples/01-layered-comparison/
├── pom.xml
├── README.md
├── src/main/java/com/ddd/layered/
│   ├── DddLayeredApplication.java              # 主类
│   ├── mvc/                                      # MVC 风格包
│   │   ├── controller/MvcOrderController.java
│   │   ├── service/MvcOrderService.java
│   │   └── model/MvcOrder.java
│   ├── three/                                    # 三层风格包
│   │   ├── controller/ThreeTierOrderController.java
│   │   ├── service/ThreeTierOrderService.java
│   │   ├── mapper/ThreeTierOrderMapper.java
│   │   └── entity/ThreeTierOrder.java
│   ├── ddd/                                      # DDD 四层风格包（核心）
│   │   ├── interfaces/web/DddOrderController.java
│   │   ├── application/service/DddOrderAppService.java
│   │   ├── domain/model/Order.java                # 聚合
│   │   ├── domain/model/Money.java
│   │   ├── domain/repository/OrderRepository.java
│   │   └── infrastructure/MybatisOrderRepository.java
│   ├── clean/                                    # 整洁架构风格包
│   │   ├── adapters/in/web/CleanOrderController.java
│   │   ├── adapters/out/persistence/CleanOrderPersistenceAdapter.java
│   │   ├── usecases/SubmitOrderUseCase.java
│   │   ├── entities/Order.java
│   │   └── frameworks/DddLayeredApplication.java
│   ├── hexagonal/                                # 六边形风格包
│   │   ├── adapters/in/web/HexOrderController.java
│   │   ├── adapters/in/cli/HexOrderCliRunner.java  # 额外入口
│   │   ├── adapters/out/persistence/HexOrderRepositoryAdapter.java
│   │   ├── domain/Order.java
│   │   ├── ports/in/SubmitOrderPort.java
│   │   └── ports/out/OrderRepositoryPort.java
│   └── comparison/                              # 横向对比 Demo（多 Profile 切换）
│       └── ComparisonRunner.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-mvc.yml      # 激活 MVC 风格
│   ├── application-three.yml    # 激活三层风格
│   ├── application-ddd.yml      # 激活 DDD 风格
│   ├── application-clean.yml    # 激活整洁风格
│   ├── application-hex.yml      # 激活六边形风格
│   └── schema-h2.sql             # H2 建表（内存数据库）
└── src/test/java/                  # 领域层单元测试 1 个
    └── domain/OrderTest.java
```

**技术栈**：H2 内存数据库 + MyBatis-Plus，无外部依赖，直接 `mvn spring-boot:run -Dspring-boot.run.profiles=ddd` 即可。

**核心验证**：通过 `ComparisonRunner` 跑同一用例，对比六种分层的代码行数、文件数、依赖数。

---

### examples/02-modular-monolith（73-75 章配套）

**目标**：演示 Maven 多模块单体——user / order / inventory / payment 四个模块 + 共享 common 模块 + Spring Boot 应用模块，单进程启动。

**核心演示**：
- 父 POM 统一管理依赖版本
- 每个业务模块独立 `domain / application / infrastructure / interfaces` 包结构
- 模块间只能通过 `application-service` 调用对方 `application-api`，**禁止**反向依赖 `infrastructure`
- ArchUnit 测试守护"禁止跨模块 internal 包 import"

**最低文件清单**：
```
examples/02-modular-monolith/
├── pom.xml                          # 父 POM
├── README.md
├── docker-compose.yml                # MySQL + Redis + 应用
├── sql/schema.sql                    # 建表
├── modular-common/                   # 共享：通用 DTO、异常、Util
│   └── src/main/java/com/ddd/modular/common/
│       ├── dto/PageResult.java
│       ├── exception/BusinessException.java
│       └── util/MoneyUtils.java
├── modular-user/                     # 用户上下文
│   ├── pom.xml
│   └── src/main/java/com/ddd/modular/user/
│       ├── user/domain/model/User.java
│       ├── user/domain/repository/UserRepository.java
│       ├── user/application/api/UserQueryService.java    # 对外暴露接口
│       ├── user/application/service/UserAppService.java
│       └── user/infrastructure/MybatisUserRepository.java
├── modular-order/                    # 订单上下文
│   ├── pom.xml
│   └── src/main/java/com/ddd/modular/order/
│       ├── order/domain/model/Order.java
│       ├── order/domain/model/OrderItem.java
│       ├── order/domain/repository/OrderRepository.java
│       ├── order/application/api/OrderQueryService.java
│       ├── order/application/service/OrderAppService.java
│       └── order/infrastructure/MybatisOrderRepository.java
├── modular-inventory/                # 库存上下文（结构同上）
├── modular-payment/                  # 支付上下文（结构同上）
├── modular-app/                      # Spring Boot 启动模块
│   ├── pom.xml
│   ├── src/main/java/com/ddd/modular/ModularApplication.java
│   ├── src/main/resources/application.yml
│   └── src/main/resources/mapper/...
└── modular-archunit/                  # ArchUnit 守护测试
    └── src/test/java/com/ddd/modular/archunit/
        └── ModuleBoundaryRulesTest.java
```

**关键依赖关系**：
```
modular-app → modular-order, modular-inventory, modular-payment, modular-user
modular-order → modular-common（不能依赖 modular-internal）
modular-inventory → modular-common
modular-payment → modular-common, modular-order.api（仅 application-api）
```

---

### examples/03-ecommerce-order（76-78 章配套）

**目标**：以订单上下文为载体，演示完整的 DDD 战术实现——聚合充血、仓储倒置、状态机、领域事件、防腐层。

**核心演示**：
- 订单聚合充血实现（含 OrderItem、Money 值对象）
- 仓储接口定义在领域层、MyBatis 实现在基础设施层（PO + Assembler + Mapper）
- 状态机：PENDING → PAID → SHIPPED → COMPLETED + 超时关单
- 5 个领域事件 + 事件发布（进程内 ApplicationEventPublisher）
- 调用 user / product 上下文的防腐层（ACL）

**最低文件清单**：
```
examples/03-ecommerce-order/
├── pom.xml
├── README.md
├── docker-compose.yml                # MySQL + Redis + 应用
├── Dockerfile
├── sql/schema.sql                    # 订单相关表
├── src/main/java/com/ddd/ecommerce/order/
│   ├── DddEcommerceOrderApplication.java
│   ├── domain/model/
│   │   ├── Order.java                # 聚合根（充血）
│   │   ├── OrderItem.java            # 内部实体
│   │   ├── Money.java                # 值对象
│   │   ├── OrderStatus.java          # 枚举
│   │   ├── OrderId.java              # 标识值对象
│   │   ├── CustomerId.java
│   │   └── event/
│   │       ├── OrderCreatedEvent.java
│   │       ├── OrderPaidEvent.java
│   │       ├── OrderCancelledEvent.java
│   │       └── OrderShippedEvent.java
│   ├── domain/repository/
│   │   └── OrderRepository.java       # 仓储接口
│   ├── domain/service/
│   │   └── OrderDomainService.java    # 领域服务（跨对象规则）
│   ├── application/service/
│   │   └── OrderAppService.java       # 应用服务（5 个用例）
│   ├── application/command/
│   │   ├── SubmitOrderCommand.java
│   │   ├── PayOrderCommand.java
│   │   ├── CancelOrderCommand.java
│   │   └── RefundOrderCommand.java
│   ├── application/query/
│   │   └── OrderQueryService.java     # CQRS 读侧
│   ├── infrastructure/persistence/
│   │   ├── OrderPO.java                # 持久化对象
│   │   ├── OrderItemPO.java
│   │   ├── OrderMapper.java            # MyBatis Mapper
│   │   ├── OrderItemMapper.java
│   │   ├── OrderAssembler.java          # PO ↔ 领域对象
│   │   └── MybatisOrderRepository.java
│   ├── infrastructure/messaging/
│   │   └── OrderEventPublisher.java    # 进程内事件发布
│   ├── infrastructure/external/
│   │   ├── UserServiceClient.java       # 远程用户服务接口
│   │   └── ProductServiceClient.java    # 远程商品服务接口
│   ├── interfaces/web/
│   │   └── OrderController.java         # REST API
│   └── interfaces/dto/
│       ├── OrderRequestDTO.java
│       └── OrderResponseDTO.java
├── src/main/resources/
│   ├── application.yml
│   └── mapper/
│       ├── OrderMapper.xml
│       └── OrderItemMapper.xml
└── src/test/java/com/ddd/ecommerce/order/
    ├── domain/OrderTest.java             # 领域层纯内存测试 5+ 个
    ├── application/OrderAppServiceTest.java
    └── interfaces/OrderControllerTest.java  # @WebMvcTest
```

**最低代码行数**：800+ 行 Java 代码（不含 pom.xml）。

---

### examples/04-saga-payment（78-80 章配套）

**目标**：演示 Saga 跨服务协调——订单 → 库存预留 → 支付扣款 的最终一致性集成。

**核心演示**：
- OrderSagaStateMachine：编排式 Saga 协调者（基于 Spring StateMachine）
- Outbox 模式：`outbox_event` 表 + 定时扫描投递
- 三个 Spring Boot 应用（订单 / 库存 / 支付）独立部署，但本工程是**整合测试形态**：单工程多 Profile 启动
- Saga 死信处理 + 补偿日志

**最低文件清单**：
```
examples/04-saga-payment/
├── pom.xml
├── README.md
├── docker-compose.yml                # MySQL + Redis + RocketMQ(可选) + 应用
├── sql/schema.sql                    # 订单/库存/支付/Saga/Outbox 表
├── src/main/java/com/ddd/saga/
│   ├── DddSagaApplication.java
│   ├── order/                          # 订单上下文（精简版，引用 03 工程）
│   │   ├── domain/Order.java
│   │   └── application/OrderAppService.java
│   ├── inventory/                      # 库存上下文
│   │   ├── domain/Inventory.java
│   │   └── application/InventoryAppService.java
│   ├── payment/                        # 支付上下文
│   │   ├── domain/Account.java
│   │   └── application/PaymentAppService.java
│   ├── saga/                           # Saga 协调
│   │   ├── OrderSagaStateMachine.java
│   │   ├── OrderSagaCoordinator.java
│   │   └── saga-log/SagaLogEntry.java
│   ├── outbox/                         # Outbox 模式
│   │   ├── OutboxEvent.java
│   │   ├── OutboxEventPO.java
│   │   ├── OutboxRepository.java
│   │   ├── OutboxWriter.java
│   │   ├── OutboxScheduler.java         # 定时扫描投递
│   │   └── OutboxDispatcher.java
│   ├── compensation/                   # 补偿逻辑
│   │   ├── InventoryCompensationService.java
│   │   └── PaymentCompensationService.java
│   └── interfaces/
│       ├── OrderController.java
│       ├── InventoryController.java
│       └── PaymentController.java
├── src/main/resources/
│   ├── application.yml
│   └── application-*.yml               # 多 Profile
└── src/test/java/com/ddd/saga/
    └── saga/OrderSagaIntegrationTest.java   # 端到端集成测试
```

**最低代码行数**：600+ 行 Java 代码。

---

## 各 sub-agent 工作量分配

| sub-agent | 章节 | 示例工程 | 预计产出 |
| --- | --- | --- | --- |
| 1 | 71 + 72（2 章） | 01-layered-comparison | 2 篇 markdown + 1 个工程 |
| 2 | 73 + 74 + 75（3 章） | 02-modular-monolith | 3 篇 markdown + 1 个工程 |
| 3 | 76 + 77 + 78（3 章） | 03-ecommerce-order | 3 篇 markdown + 1 个工程 |
| 4 | 79 + 80（2 章） | 04-saga-payment | 2 篇 markdown + 1 个工程 |

**合计**：10 篇 markdown + 4 个示例工程。

---

## 写作时的硬性提醒

1. **不要重复 40-46 章已有内容**——本专题是"分层架构视角下的整合与深化"，不是"再讲一遍 DDD"。
2. **每章必须有"分层架构视角"**——告诉读者"这种模式在分层架构中如何落地"，而不是单纯讲模式本身。
3. **示例工程之间要形成体系**——01 是"分层架构对比"、02 是"模块化单体"、03 是"单领域 DDD 完整实战"、04 是"跨领域 Saga 集成"。读者按 01→02→03→04 顺序跑通即可建立完整认知。
4. **每章末尾"配套"必须包含至少 2 个前置章节号**——形成显式知识网络。
5. **代码片段必须能在 Java 17 + Boot 3.2.5 下编译**——不允许出现过时 API（如 `javax.*` 应改为 `jakarta.*`）。