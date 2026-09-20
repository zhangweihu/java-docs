# examples/01 - 分层架构对比（DDD / Clean / Hexagonal）

> **定位**：用同一业务（订单的"创建 + 支付"）演示 3 种主流分层架构的代码差异——DDD 四层、整洁架构、六边形架构。
> **配套章节**：[第 71 章 分层架构全景](../../java-learning/71-分层架构全景.md)、[第 72 章 整洁架构落地](../../java-learning/72-整洁架构落地.md)

## 一、技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | H2 内存数据库 |
| 工具 | Lombok |

## 二、运行步骤

```bash
# 默认 Profile（DDD 四层）
mvn spring-boot:run

# 切换到整洁架构风格
mvn spring-boot:run -Dspring-boot.run.profiles=clean

# 切换到六边形风格
mvn spring-boot:run -Dspring-boot.run.profiles=hex
```

访问 H2 控制台：<http://localhost:8080/h2-console>（JDBC URL: `jdbc:h2:mem:ddd_demo`）

## 三、目录结构

```
src/main/java/com/ddd/layered/
├── DddLayeredApplication.java                 ← 主类
├── ddd/                                       ← DDD 四层风格
│   ├── interfaces/web/DddOrderController.java
│   ├── application/service/DddOrderAppService.java
│   ├── application/command/SubmitOrderCommand.java
│   ├── domain/model/Order.java                ← 聚合根（充血）
│   ├── domain/model/Money.java                ← 值对象（record）
│   ├── domain/model/OrderStatus.java
│   ├── domain/model/OrderItem.java
│   ├── domain/repository/OrderRepository.java ← 仓储接口
│   └── infrastructure/persistence/MybatisOrderRepository.java
├── clean/                                     ← 整洁架构风格
│   ├── interfaces/web/CleanOrderController.java
│   ├── application/SubmitOrderUseCase.java    ← UseCase 接口
│   ├── entities/Order.java                    ← 实体（充血）
│   └── infrastructure/MybatisOrderRepository.java
└── hexagonal/                                 ← 六边形风格
    ├── adapters/in/web/HexOrderController.java   ← 主适配器
    ├── adapters/out/persistence/HexOrderRepositoryAdapter.java   ← 从适配器
    ├── application/OrderService.java          ← 实现 Port
    ├── domain/Order.java                      ← 业务核心
    ├── ports/in/SubmitOrderPort.java          ← 入口端口
    └── ports/out/OrderRepositoryPort.java     ← 出口端口
```

## 四、核心对照（同业务三种实现）

| 用例 | DDD 四层 | 整洁架构 | 六边形 |
| --- | --- | --- | --- |
| Controller 路径 | `/ddd/orders` | `/clean/orders` | `/hex/orders` |
| 入口接口 | `DddOrderAppService`（注入） | `SubmitOrderUseCase`（注入接口） | `SubmitOrderPort`（注入端口） |
| 业务核心 | `Order`（充血） | `Order`（充血） | `Order`（充血）+ `OrderService` 实现 Port |
| 仓储 | `OrderRepository` 接口 + `MybatisOrderRepository` | 同左 | `OrderRepositoryPort` + `HexOrderRepositoryAdapter` |

## 五、代码统计

| 维度 | DDD | Clean | Hex |
| --- | --- | --- | --- |
| 包数 | 5 | 4 | 6 |
| 文件数 | 10 | 5 | 7 |
| 关键差异点 | 命令/查询分离 | UseCase 接口 | 入口/出口端口命名清晰 |

> **核心观察**：三者**业务规则完全一致**，差异只在**边界命名 + 包组织**。具体选用哪种，取决于团队命名习惯与项目长期演进预期。