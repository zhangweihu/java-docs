# examples/02 - 模块化单体（user / order / inventory / payment 四模块）

> **定位**：演示 Maven 多模块单体——单进程 + 强边界 + 各自领域 + 未来可拆微服务。
> **配套章节**：[第 75 章 Modular Monolith 落地](../../java-learning/75-ModularMonolith落地.md)

## 一、技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | MySQL 8.0（兼容 H2） |
| 工具 | Lombok |
| 守护 | ArchUnit 1.3.0 |

## 二、模块结构

```
modular-monolith/
├── pom.xml                          ← 父 POM
├── modular-common/                  ← 共享（DTO / 异常 / Util）
├── modular-user/                    ← 用户上下文
├── modular-order/                   ← 订单上下文
├── modular-inventory/                ← 库存上下文
├── modular-payment/                 ← 支付上下文
├── modular-app/                     ← Spring Boot 启动
└── modular-archunit/                ← 边界守护测试
```

## 三、运行步骤

```bash
# 1. 启动 MySQL（或使用 H2 内存库）
docker compose up -d mysql

# 2. 编译 + 运行
mvn clean package -DskipTests
java -jar modular-app/target/modular-app-1.0.0.jar

# 3. 运行 ArchUnit 边界测试
mvn -pl modular-archunit test
```

## 四、模块依赖关系

```
modular-app ───┬─→ modular-user ──→ modular-common
               ├─→ modular-order ──→ modular-common
               ├─→ modular-inventory ──→ modular-common
               └─→ modular-payment ──→ modular-common

modular-archunit ──→ modular-app（测试依赖）
```

**严格约束**：
- 业务模块之间**禁止**直接 `dependency` 对方
- 跨模块通信只能通过 `application.api` 接口
- 业务模块**禁止**依赖 `modular-app`

## 五、每个模块四层包结构

```
com.ddd.modular.{context}/
├── domain/
│   ├── model/                ← 实体、值对象
│   └── repository/           ← 仓储接口
├── application/
│   ├── api/                  ← 对外暴露（其他模块可读）
│   └── service/              ← 应用服务（编排）
├── infrastructure/
│   └── persistence/          ← MyBatis PO/Mapper/仓储实现
└── interfaces/
    └── web/                  ← Controller（仅 modular-app 引用）
```

## 六、ArchUnit 守护规则（modular-archunit）

| 规则 | 约束 |
| --- | --- |
| 业务模块之间禁止跨边界依赖 `infrastructure` | 模块边界保护 |
| `domain` 包禁止依赖 `application` / `infrastructure` | 分层保护 |
| `*Repository` 类必须是接口 | 仓储倒置 |
| Controller 禁止直接依赖 Repository | 应用服务编排约束 |
| 业务模块禁止依赖 `modular-app` | 防止反向依赖 |

> 任何违反规则的提交会被 CI 阻断。

## 七、与 mall-cloud/ 的对照

| 维度 | examples/02（模块化单体） | mall-cloud（微服务） |
| --- | --- | --- |
| 进程数 | 1 | 5+ |
| 部署频率 | 单体 | 各服务独立 |
| 启动时间 | ~10s | ~30s（5 服务） |
| 调试 | 单进程断点 | 跨服务链路追踪 |
| 适用阶段 | 5~10 人团队 | 10+ 人团队 |
| 演进路径 | 直接拆为 mall-cloud | — |