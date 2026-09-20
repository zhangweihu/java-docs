# DDD × 分层架构 专题（71~80 章）

> **定位**：本专题是 `java-learning/` 70 章主线之**后**的"分层架构与 DDD 工程化"深度专题——10 章正文 + 4 个可运行 Spring Boot 示例工程。
> **目标读者**：已学完 `java-learning/` 第 40~46 章（贫血模型、DDD 战术、状态机、CQRS/ES、事件风暴、Axon、分布式事务）与第 70 章（DDD×报表）的读者，希望建立**分层架构视角下**的工程化能力，能把分散的 DDD 模式串成完整的项目骨架。

## 一、为什么需要"分层架构 × DDD"

`java-learning/` 40~46 章已系统覆盖 DDD 战术（实体/值对象/聚合/仓储）、CQRS/事件溯源、Axon 框架、分布式事务等模式。但读者实操时会遇到三个典型难题：

1. **"模式都懂，工程不会组装"**——知道聚合充血，但 Spring Boot 工程里究竟把聚合根放在哪个包？应用服务 vs 领域服务怎么协作？事务放哪？
2. **"分层方案选哪个"**——MVC、三层、DDD 四层、整洁架构、六边形、模块化单体，六种方案各自适用什么场景？
3. **"多上下文如何集成"**——订单、库存、支付三个限界上下文在 Spring Boot 工程里如何共存？模块边界如何守护？跨服务如何用 Saga 协调？

本专题就是为回答这三个问题而写。

## 二、章节目录

| 章节 | 标题 | 配套示例工程 |
| --- | --- | --- |
| [第七十一章](./71-分层架构全景.md) | 分层架构全景（MVC → 三层 → DDD → 整洁 → 六边形 → 模块化单体） | [examples/01-layered-comparison](./examples/01-layered-comparison/) |
| [第七十二章](./72-整洁架构落地.md) | 整洁架构（Clean Architecture）落地 | 同上 |
| [第七十三章](./73-六边形架构.md) | 六边形架构（Ports & Adapters） | [examples/02-modular-monolith](./examples/02-modular-monolith/) |
| [第七十四章](./74-应用服务与CQRS入口.md) | 应用服务与 CQRS 入口（UseCase 编排） | 同上 |
| [第七十五章](./75-ModularMonolith落地.md) | Modular Monolith 落地 | 同上 |
| [第七十六章](./76-订单领域实战.md) | 订单领域实战（聚合 + 状态机 + 事件 + ACL） | [examples/03-ecommerce-order](./examples/03-ecommerce-order/) |
| [第七十七章](./77-库存领域实战.md) | 库存领域实战（预留/释放/防超卖） | 同上 |
| [第七十八章](./78-支付领域实战.md) | 支付领域实战（账户/金额/Saga 入口） | 同上 |
| [第七十九章](./79-跨上下文集成SagaOutbox.md) | 跨上下文集成（Saga / Outbox / 可观测） | [examples/04-saga-payment](./examples/04-saga-payment/) |
| [第八十章](./80-专题收官演进路线.md) | 专题收官（演进路线 / 边界守护 / 面试冲刺） | 同上 |

> **章节文件命名**：与 `java-learning/` 主线风格完全一致（`7X-标题.md`，无空格）。

## 三、示例工程索引

> 4 个工程均遵循 [`CONVENTIONS.md`](./CONVENTIONS.md) 共享约定（Spring Boot 3.2.5 + Java 17 + MyBatis-Plus 3.5.6 + Maven）。

| 工程 | 定位 | 启动方式 |
| --- | --- | --- |
| [01-layered-comparison](./examples/01-layered-comparison/) | 同一订单业务在 6 种分层方案下的代码对照 | H2 内存库，多 Profile 切换（`mvn spring-boot:run -Dspring-boot.run.profiles=ddd`） |
| [02-modular-monolith](./examples/02-modular-monolith/) | Maven 多模块单体（user / order / inventory / payment） | `docker compose up -d --build` |
| [03-ecommerce-order](./examples/03-ecommerce-order/) | 订单上下文完整 DDD 实战（聚合/状态机/事件/ACL） | `docker compose up -d --build` |
| [04-saga-payment](./examples/04-saga-payment/) | 跨订单-库存-支付的 Saga 编排与 Outbox 模式 | `docker compose up -d --build` |

每个工程目录下都有独立 `README.md`，包含技术栈、运行步骤、目录结构、代码导读。

## 四、与 `java-learning/` 主线的衔接

本专题不重复 `java-learning/` 40~46/70 章已有内容，而是从"分层架构视角"对它们做工程化整合与深化：

| 主线章节 | 本专题对应 |
| --- | --- |
| 第 40 章 贫血模型与充血模型 | 第 71 章"DDD 四层"、第 76 章"聚合充血实现" |
| 第 41 章 DDD 战术设计 | 第 73 章"端口与仓储接口"、第 76~78 章"三个聚合实战" |
| 第 42 章 Spring StateMachine | 第 76 章"订单状态机"、第 79 章"Saga 状态机" |
| 第 43 章 CQRS 与事件溯源 | 第 74 章"应用服务 CQRS 入口" |
| 第 44 章 事件风暴工作坊 | 第 76 章"通用语言" |
| 第 45 章 Axon Framework 实战 | 第 79 章"事件总线选择" |
| 第 46 章 分布式事务与 Saga | 第 79 章"Saga 实战" |
| 第 70 章 DataMax 报表与 DDD | 第 80 章"读侧 CQRS 与报表模块的衔接思路" |

## 五、学习路线建议

```
学习文档第 71 章 → 跑通 examples/01（6 种分层对比） → 第 72~75 章 → 跑通 examples/02（模块化单体）
     → 第 76~78 章 → 跑通 examples/03（订单完整实战） → 第 79 章 → 跑通 examples/04（Saga 集成）
     → 第 80 章专题收官 → 完成"分层架构 × DDD"完整闭环
```

预计耗时 2~3 周。

## 六、约定与大纲

- **编写约定**：[CONVENTIONS.md](./CONVENTIONS.md)（技术栈、命名、风格基线）
- **章节大纲**：[OUTLINE.md](./OUTLINE.md)（每章核心要点 + 工程最低交付清单）

---

至此，DDD × 分层架构专题入口已就绪。下一章 [71-分层架构全景.md](./71-分层架构全景.md)。