# examples/03 - 订单领域完整 DDD 实战

> **定位**：以订单领域为载体，演示完整的 DDD 战术实现——聚合充血、仓储倒置、状态机、领域事件、防腐层。
> **配套章节**：[第 76 章 订单领域实战](../../java-learning/76-订单领域实战.md)、[第 77 章 库存领域实战](../../java-learning/77-库存领域实战.md)、[第 78 章 支付领域实战](../../java-learning/78-支付领域实战.md)

## 一、技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| 状态机 | Spring StateMachine 4.0 |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | H2 内存（可换 MySQL） |
| 守护 | ArchUnit 1.3.0 |

## 二、运行步骤

```bash
# 默认 H2 内存数据库
mvn spring-boot:run

# 访问 H2 控制台
open http://localhost:8080/h2-console
```

## 三、订单领域 5 大核心场景

| 场景 | 触发 | 状态变化 | 副作用 |
| --- | --- | --- | --- |
| 创建订单 | POST /orders | (new) → PENDING | 库存预留 + 金额冻结 |
| 支付订单 | POST /orders/{id}/pay | PENDING → PAID | 库存扣减 + 金额扣款 |
| 取消订单 | POST /orders/{id}/cancel | 任意 → CANCELLED | 库存释放 + 金额解冻 |
| 发货 | POST /orders/{id}/ship | PAID → SHIPPED | （无） |
| 完成 | POST /orders/{id}/complete | SHIPPED → COMPLETED | （无） |

## 四、目录结构

```
com.ddd.ecommerce.order/
├── domain/
│   ├── model/Order.java                 ← 聚合根（充血 + 7 个行为方法）
│   ├── model/OrderItem.java             ← 内部实体
│   ├── model/Money.java                 ← 值对象（record）
│   ├── model/OrderStatus.java           ← 枚举
│   ├── model/OrderId.java               ← 强类型 ID
│   ├── model/CustomerId.java
│   └── repository/OrderRepository.java  ← 仓储接口
├── application/
│   ├── service/OrderAppService.java     ← 5 个用例
│   ├── command/SubmitOrderCommand.java
│   └── query/OrderQueryService.java
├── infrastructure/
│   ├── persistence/OrderPO.java
│   ├── persistence/OrderMapper.java
│   ├── persistence/OrderPOAssembler.java
│   └── persistence/MybatisOrderRepository.java
└── interfaces/web/OrderController.java  ← REST API
```

## 五、API 文档

```
POST   /orders              创建订单
GET    /orders/{id}         查询订单
POST   /orders/{id}/pay     支付订单
POST   /orders/{id}/cancel  取消订单
POST   /orders/{id}/ship    发货
POST   /orders/{id}/complete 完成订单
```

## 六、与 examples/02 的关系

| 维度 | examples/02 | examples/03 |
| --- | --- | --- |
| 架构 | 模块化单体（4 模块） | 单体（订单单领域深度） |
| 重点 | 模块边界 + ArchUnit | 订单领域全栈实现 |
| 状态机 | 简化（OrderStatus 字段校验） | 完整（Spring StateMachine） |
| 事件 | 进程内 ApplicationEventPublisher | 进程内 + Outbox（可选） |
| 上下文集成 | 同进程调用 | 端口 + 防腐层 |