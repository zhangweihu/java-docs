# 第四十二章 Spring StateMachine 状态机（业务状态流转工程化）

> 本章目标：理解状态机（State Machine）的核心概念（状态 / 事件 / 迁移 / 守卫 / 动作），掌握 Spring StateMachine 框架的完整用法——配置状态机、事件驱动流转、Guard 守卫、Action 动作、持久化、监听与扩展，并用它实现"订单状态机"实战（与第 40 章手写 `ensureStatus`、第 14 章状态模式对照），能独立判断"什么业务该上状态机、什么业务不该上"。
>
> 前置知识：第四十章 贫血模型与充血模型（`ensureStatus` 手写状态校验）、第四十一章 DDD 战术设计（聚合内状态是聚合根的一部分）、第十四章 设计模式（状态模式）、第九章 Spring Boot。

## 42.1 为什么需要状态机框架

### 42.1.1 回顾：第 40 章的手写状态校验

第 40 章充血模型里，我们这样表达状态流转：

```java
public class Order {
    public void pay() { ensureStatus(PENDING, "支付"); this.status = PAID; }
    public void cancel() { ensureStatus(PENDING, "取消"); this.status = CANCELLED; }
    private void ensureStatus(int expected, String action) {
        if (this.status != expected) throw new IllegalStateException("当前状态不允许" + action);
    }
}
```

手写方案的优点：**简单、纯内存、可单测**。但当状态和事件多了之后，问题就来了：

### 42.1.2 手写 `if/else` 的五大痛点

| # | 痛点 | 表现 |
| --- | --- | --- |
| 1 | **流转图不可见** | 状态/事件关系散落在 N 个方法里，没人能一眼画出状态图 |
| 2 | **非法迁移只能靠人自觉** | `ensureStatus` 只拦了部分，`PAID → CANCELLED` 这种"绕过"靠漏 |
| 3 | **无全局视角** | 新同事不知道"订单一共几个状态、哪些迁移合法" |
| 4 | **动作与守卫混在业务里** | "支付前校验"“支付后发通知”和状态流转写在一起 |
| 5 | **不可审计** | 谁、什么时候、因为什么事件变到哪个状态，没有统一记录 |

> **典型事故**：订单系统手写 `if`，状态多了之后某处漏判，出现"已取消订单被支付成功"——状态机框架的价值就是**把这张"合法迁移表"声明出来，非法迁移从机制上不可能发生**。

### 42.1.3 状态机是什么

**有限状态机（FSM）** 四要素：

```
┌─────────┐  事件(Event) + 守卫(Guard)   ┌─────────┐
│ 状态 S1  │ ──────────────────────────► │ 状态 S2  │
└─────────┘        执行动作(Action)      └─────────┘

- 状态（State）：当前处于什么阶段（待支付/已支付/已取消…）
- 事件（Event）：什么触发流转（支付/取消/发货…）
- 迁移（Transition）：S1 --事件--> S2 的合法边
- 守卫（Guard）：允许迁移的条件（满足才走）
- 动作（Action）：迁移过程中执行的行为（发通知/记账…）
```

**一条铁律**：**同一状态 + 同一事件，结果永远确定**——这正是状态机"可控、可测、可审计"的根本原因。

## 42.2 Spring StateMachine 概览

### 42.2.1 是什么

Spring StateMachine 是 Spring 官方的状态机框架（`spring-statemachine`），提供：

- **声明式配置**：用 Java Config 声明状态、事件、迁移，状态图即代码；
- **事件驱动**：`stateMachine.sendEvent(event)` 驱动流转，自动校验合法迁移；
- **Guard/Action**：迁移前守卫、迁移时动作，横切关注点与状态分离；
- **持久化**：把状态机状态存到数据库，重启/分布式中断后恢复；
- **扩展点**：监听器、拦截器、状态机工厂，可观测可审计。

### 42.2.2 依赖引入（Maven）

```xml
<dependency>
    <groupId>org.springframework.statemachine</groupId>
    <artifactId>spring-statemachine-core</artifactId>
    <version>4.0.0</version>   <!-- 4.x 为当前稳定主线 -->
</dependency>
```

> 版本说明：Spring StateMachine 4.x 需要 Spring Boot 3.x（JDK 17+）。老项目 Boot 2.x 用 3.x 版本线。配置类注解风格略有差异（4.x 支持 `@EnableStateMachineFactory`，推荐用工厂方式，避免状态机单例冲突）。

## 42.3 第一个状态机：订单状态机（核心配置）

### 42.3.1 定义状态与事件枚举

```java
// 订单状态（与 41.8 的聚合状态对齐）
public enum OrderStates {
    PENDING,      // 待支付（创建后初始态）
    SUBMITTED,    // 已提交（锁定商品）
    PAID,         // 已支付
    CANCELLED,    // 已取消
    REFUNDED      // 已退款
}

// 触发事件
public enum OrderEvents {
    SUBMIT,       // 提交
    PAY,          // 支付
    CANCEL,       // 取消
    REFUND        // 退款
}
```

### 42.3.2 状态机配置（声明合法迁移表）

```java
@Configuration
@EnableStateMachineFactory            // 工厂模式：每个业务对象一个状态机实例（推荐！）
public class OrderStateMachineConfig
        extends StateMachineConfigurerAdapter<OrderStates, OrderEvents> {

    @Override
    public void configure(StateMachineStateConfigurer<OrderStates, OrderEvents> states) throws Exception {
        states.withStates()
                .initial(OrderStates.PENDING)          // 初始状态
                .state(OrderStates.SUBMITTED)          // 普通状态
                .state(OrderStates.PAID)
                .state(OrderStates.CANCELLED)
                .state(OrderStates.REFUNDED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderStates, OrderEvents> transitions) throws Exception {
        transitions
                // 迁移表：PENDING --SUBMIT--> SUBMITTED（含守卫和动作）
                .withExternal()
                    .source(OrderStates.PENDING).target(OrderStates.SUBMITTED)
                    .event(OrderEvents.SUBMIT)
                    .guard(orderSubmitGuard())          // 守卫：提交前校验
                    .action(submitAction())             // 动作：提交后处理
                    .and()
                // PENDING --PAY--> PAID
                .withExternal()
                    .source(OrderStates.PENDING).target(OrderStates.PAID)
                    .event(OrderEvents.PAY)
                    .guard(orderPayGuard())
                    .action(payAction())
                    .and()
                // PENDING --CANCEL--> CANCELLED
                .withExternal()
                    .source(OrderStates.PENDING).target(OrderStates.CANCELLED)
                    .event(OrderEvents.CANCEL)
                    .and()
                // PAID --REFUND--> REFUNDED
                .withExternal()
                    .source(OrderStates.PAID).target(OrderStates.REFUNDED)
                    .event(OrderEvents.REFUND)
                    .guard(refundGuard())
                    .action(refundAction());
    }

    // 注入的状态机 Bean 容器（配合持久化时使用）
    @Bean
    public StateMachinePersister<OrderStates, OrderEvents, String> persister(
            StateMachinePersist<OrderStates, OrderEvents, String> persist) {
        return new DefaultStateMachinePersister<>(persist);
    }
}
```

> **这张配置表就是订单的"宪法"**：PAID 能不能 CANCEL？配置里没有这条边 → 框架直接拒绝。漏写迁移 = 业务不允许，从机制上杜绝"非法状态"。

### 42.3.3 守卫（Guard）与动作（Action）

```java
// 守卫：决定"这个事件现在允不允许发生"
@Component
public class OrderPayGuard implements Guard<OrderStates, OrderEvents> {
    @Override
    public boolean evaluate(StateContext<OrderStates, OrderEvents> context) {
        // 从上下文取出业务对象（持久化时用 context.getExtendedState() 传递）
        Order order = context.getExtendedState().get("order", Order.class);
        return order != null && order.getTotalAmount().compareTo(BigDecimal.ZERO) > 0;
    }
}

// 动作：迁移发生时执行（可在 before/after 回调里做横切逻辑）
@Component
public class PayAction implements Action<OrderStates, OrderEvents> {
    @Override
    public void execute(StateContext<OrderStates, OrderEvents> context) {
        Order order = context.getExtendedState().get("order", Order.class);
        // 迁移前（preTransition）：调用支付平台、记账……
        // 迁移后（postTransition）：发支付成功事件、通知下游……
        notificationService.sendPaidNotice(order);
    }
}
```

### 42.3.4 使用状态机：事件驱动流转

```java
@Service
public class OrderStateMachineService {
    @Autowired private StateMachineFactory<OrderStates, OrderEvents> factory;

    public OrderStates pay(Long orderId) {
        // 1. 为这个订单创建"独立状态机实例"（工厂模式，互不干扰）
        StateMachine<OrderStates, OrderEvents> sm = factory.getStateMachine("order:" + orderId);
        sm.start();
        // 2. 把业务对象塞进扩展状态（Guard/Action 里取）
        Order order = orderRepository.findById(orderId);
        sm.getExtendedState().getVariables().put("order", order);
        // 3. 发送事件：合法迁移 → 执行 Guard/Action；非法 → 抛异常
        boolean accepted = sm.sendEvent(OrderEvents.PAY);
        if (!accepted) {
            throw new BusinessException(400, "订单状态不允许支付，当前状态：" + sm.getState().getId());
        }
        // 4. 读结果状态并落库
        OrderStates next = sm.getState().getId();
        orderRepository.updateStatus(orderId, next);
        return next;
    }
}
```

## 42.4 Guard / Action / 监听器的三种写法对比

### 42.4.1 内联 Lambda vs 独立 Bean

| 方式 | 代码位置 | 适用 |
| --- | --- | --- |
| Lambda 内联 | 配置类里 `.guard(ctx -> ...)` | 简单条件（一次性） |
| 独立 @Component | 实现 Guard/Action 接口 | 复杂逻辑、可复用、可单测 |
| SpEL 表达式 | `.guardExpression("...")` | 极简条件 |

```java
// 方式一：Lambda（简单守卫）
.withExternal()
    .source(OrderStates.PENDING).target(OrderStates.PAID)
    .event(OrderEvents.PAY)
    .guard(ctx -> ctx.getExtendedState().get("order", Order.class) != null)

// 方式三：SpEL（基于 extendedState 变量）
.withExternal()
    .source(OrderStates.PENDING).target(OrderStates.PAID)
    .event(OrderEvents.PAY)
    .guardExpression("extendedState.variables['amountValid'] == true")
```

### 42.4.2 监听器（观察状态流转全生命周期）

```java
@Component
public class OrderStateListener implements StateMachineListener<OrderStates, OrderEvents> {
    @Override
    public void stateChanged(State<OrderStates, OrderEvents> from, State<OrderStates, OrderEvents> to) {
        // 状态变化：落审计日志、发消息
        auditService.log("订单状态变化：" + from.getId() + " -> " + to.getId());
    }

    @Override
    public void eventNotAccepted(Message<OrderEvents> event) {
        // 非法事件被拒绝：记录告警（监控"谁在尝试非法操作"）
        alertService.warn("非法状态流转尝试：" + event.getPayload());
    }
    // 其余监听方法省略（stateEntered/stateExited/transition/transitionStarted...）
}
```

**监听器 vs Action 分工**：

| 对比 | Action | Listener |
| --- | --- | --- |
| 归属 | 迁移（Transition）的横切逻辑 | 状态机全局观察 |
| 触发 | 随特定迁移执行 | 所有状态/事件变化都通知 |
| 典型用途 | 调支付、记账、发通知 | 审计日志、监控告警、埋点 |
| 是否可影响流程 | 是（抛异常可中断） | 否（只读观察） |

## 42.5 状态机持久化（关键工程能力）

### 42.5.1 为什么需要持久化

状态机实例在内存中运行，**应用重启/分布式多实例后状态机实例丢失**。持久化 = 把状态机当前状态 + 扩展变量存到数据库，随时恢复。

### 42.5.2 三种持久化策略

| 策略 | 做法 | 适用 |
| --- | --- | --- |
| **状态 + 业务表**（最常用） | 业务表 `t_order.status` 就是状态机状态，每次流转后落库，重建状态机时从库里恢复 | 主流方案（见 42.5.3） |
| StateMachinePersister | 用框架的 `Persist` 组件把状态机对象整体序列化存库 | 需要保留 Guard 上下文变量 |
| 事件溯源 | 存"事件流"，重放还原状态（第 43 章） | CQRS/ES 架构 |

> **实战结论**：绝大多数业务场景**不用把状态机对象持久化**，只要"**业务表 status 列 ↔ 状态机状态**"对齐即可——每次操作先 `new` 一个状态机并 `setState(当前 status)`，再 sendEvent。简单、可靠、可审计。

### 42.5.3 实战：状态对齐 + 恢复（推荐写法）

```java
@Service
public class OrderStateMachineService {
    @Autowired private StateMachineFactory<OrderStates, OrderEvents> factory;
    @Autowired private OrderRepository orderRepository;

    public OrderStates transition(Long orderId, OrderEvents event) {
        Order order = orderRepository.findById(orderId);

        // 1. 从业务表当前状态重建状态机
        StateMachine<OrderStates, OrderEvents> sm = factory.getStateMachine("order:" + orderId);
        sm.stop();
        sm.getStateMachineAccessor().doWithAllRegions(access -> {
            access.resetStateMachine(new DefaultStateMachineContext<>(
                    order.getStatus(), null, null, null));     // 对齐到当前状态
        });
        sm.start();

        // 2. 传入业务对象
        sm.getExtendedState().getVariables().put("order", order);

        // 3. 发送事件（合法迁移才执行）
        if (!sm.sendEvent(event)) {
            throw new BusinessException(400, "非法状态流转：" + order.getStatus() + " --" + event + "--> ?");
        }

        // 4. 新状态落库（原子更新 + 乐观锁防并发）
        OrderStates next = sm.getState().getId();
        int rows = orderRepository.updateStatusIfVersion(orderId, next, order.getVersion());
        if (rows == 0) throw new BusinessException(409, "订单状态已被他人修改，请刷新重试");
        return next;
    }
}
```

### 42.5.4 完整持久化方案（StateMachinePersister，需要时再用）

```java
// 1. 定义持久化实现：把状态机快照存到自定义表
@Component
public class OrderStateMachinePersist
        implements StateMachinePersist<OrderStates, OrderEvents, String> {

    @Autowired private StateMachineRecordMapper mapper;

    @Override
    public void write(StateMachineContext<OrderStates, OrderEvents> context, String orderId) throws Exception {
        mapper.upsert(new StateMachineRecord(orderId, context.getState().name(),
                context.getExtendedState().getVariables()));
    }

    @Override
    public StateMachineContext<OrderStates, OrderEvents> read(String orderId) throws Exception {
        StateMachineRecord rec = mapper.selectById(orderId);
        if (rec == null) return null;
        return new DefaultStateMachineContext<>(OrderStates.valueOf(rec.getState()),
                null, null, rec.getVariables() == null ? Map.of() : rec.getVariables());
    }
}

// 2. 使用：save/restore 状态机
persister.save(sm, "order:" + orderId);      // 流转后保存
persister.restore(sm, "order:" + orderId);   // 下次操作前恢复
```

## 42.6 实战：完整订单状态机（含并发与超时）

### 42.6.1 场景与状态图

```
                      SUBMIT                     PAY                  REFUND
   ┌────────┐ ──────► ┌─────────┐ ───────────► ┌────────┐ ────────► ┌─────────┐
   │ PENDING│         │SUBMITTED│              │  PAID  │           │REFUNDED │
   └────────┘         └─────────┘              └────────┘           └─────────┘
       │                  │
       └──── CANCEL ──────┘
        （PENDING/SUBMITTED 都可取消）
```

**业务规则**：
- 待支付/已提交可取消；已支付不可取消，只能退款；
- 支付前校验金额 > 0 且未风控冻结；
- 每次流转记录审计日志；
- 并发下用乐观锁兜底（42.5.3 的 `updateStatusIfVersion`）。

### 42.6.2 完整实现（配置 + 守卫 + 动作 + 服务）

```java
// 配置（迁移表补上 SUBMITTED 的取消边）
@Override
public void configure(StateMachineTransitionConfigurer<OrderStates, OrderEvents> transitions) throws Exception {
    transitions
            .withExternal().source(PENDING).target(SUBMITTED).event(SUBMIT).guard(submitGuard).action(submitAction).and()
            .withExternal().source(PENDING).target(PAID).event(PAY).guard(payGuard).action(payAction).and()
            .withExternal().source(PENDING).target(CANCELLED).event(CANCEL).action(cancelAction).and()
            .withExternal().source(SUBMITTED).target(CANCELLED).event(CANCEL).action(cancelAction).and()
            .withExternal().source(PAID).target(REFUNDED).event(REFUND).guard(refundGuard).action(refundAction);
}
```

```java
// 守卫：支付前校验（结合风控）
@Component
public class PayGuard implements Guard<OrderStates, OrderEvents> {
    @Override
    public boolean evaluate(StateContext<OrderStates, OrderEvents> context) {
        Order order = context.getExtendedState().get("order", Order.class);
        if (order == null || order.getTotalAmount().signum() <= 0) return false;
        return !riskService.isFrozen(order.getCustomerId());   // 风控冻结则拒绝
    }
}

// 动作：支付成功后发事件（领域事件，衔接第 41 章最终一致性）
@Component
public class PayAction implements Action<OrderStates, OrderEvents> {
    @Override
    public void execute(StateContext<OrderStates, OrderEvents> context) {
        Order order = context.getExtendedState().get("order", Order.class);
        applicationEventPublisher.publishEvent(new OrderPaidEvent(order.getId()));  // 下游扣库存/发货
    }
}
```

```java
// 服务：对外暴露"安全流转"入口
@Service
public class OrderStateMachineService {
    public OrderStates pay(Long orderId) { return transition(orderId, OrderEvents.PAY); }
    public OrderStates cancel(Long orderId) { return transition(orderId, OrderEvents.CANCEL); }
    public OrderStates refund(Long orderId) { return transition(orderId, OrderEvents.REFUND); }
    // transition() 见 42.5.3（重建状态机 + sendEvent + 乐观锁落库）
}
```

### 42.6.3 测试（迁移表即测试依据）

```java
@SpringBootTest
class OrderStateMachineTest {
    @Autowired private StateMachineFactory<OrderStates, OrderEvents> factory;

    private StateMachine<OrderStates, OrderEvents> newMachine(OrderStates from) {
        StateMachine<OrderStates, OrderEvents> sm = factory.getStateMachine("test");
        sm.stop();
        sm.getStateMachineAccessor().doWithAllRegions(access ->
                access.resetStateMachine(new DefaultStateMachineContext<>(from, null, null, null)));
        sm.start();
        return sm;
    }

    @Test
    void 待支付_支付_成功() {
        StateMachine<OrderStates, OrderEvents> sm = newMachine(OrderStates.PENDING);
        sm.getExtendedState().getVariables().put("order", orderOf(new BigDecimal("100")));
        assertTrue(sm.sendEvent(OrderEvents.PAY));
        assertEquals(OrderStates.PAID, sm.getState().getId());
    }

    @Test
    void 已支付_取消_被拒绝() {
        StateMachine<OrderStates, OrderEvents> sm = newMachine(OrderStates.PAID);
        assertFalse(sm.sendEvent(OrderEvents.CANCEL));      // 非法迁移，机制拒绝！
        assertEquals(OrderStates.PAID, sm.getState().getId());
    }

    @Test
    void 已支付_退款_成功() {
        StateMachine<OrderStates, OrderEvents> sm = newMachine(OrderStates.PAID);
        sm.getExtendedState().getVariables().put("order", orderOf(new BigDecimal("100")));
        assertTrue(sm.sendEvent(OrderEvents.REFUND));
        assertEquals(OrderStates.REFUNDED, sm.getState().getId());
    }
}
```

## 42.7 状态机 vs 手写 if/else vs 状态模式（选型对照）

### 42.7.1 三者对比

| 对比 | 手写 if/else（40 章 ensureStatus） | 状态模式（第 14 章） | Spring StateMachine |
| --- | --- | --- | --- |
| 配置成本 | 无 | 低（类结构） | 中（框架依赖） |
| 状态图可见性 | 差（散落） | 中（类结构可见） | **好（迁移表即图）** |
| 非法迁移拦截 | 靠人自觉 | 靠结构 | **机制级拦截** |
| Guard/Action 分离 | 无 | 部分（上下文） | **完整（横切）** |
| 持久化/恢复 | 手写 | 手写 | **框架支持** |
| 审计/监听 | 手写 | 手写 | **内置监听器** |
| 学习成本 | 零 | 低 | 中高 |
| 适合场景 | 状态少（≤3）简单场景 | 状态行为分派复杂 | **状态多、迁移规则多、需审计/恢复** |

### 42.7.2 决策建议

```
状态数量 ≤ 2~3 且迁移固定？ → 手写 if/else（别过度设计）
状态多（≥4）但主要是"行为分派"？ → 状态模式（14 章）可能更轻
状态多 + 迁移规则复杂 + 要审计/持久化/并发恢复？ → Spring StateMachine
```

> **与第 40/41 章的关系**：状态机框架解决的是"**状态流转机制**"，领域对象（聚合根）依然承载业务规则。**推荐组合**：聚合根定义"状态常量 + 行为方法"，StateMachine 负责"迁移合法性 + Guard/Action + 审计"，落库仍走仓储——各司其职。

## 42.8 常见坑与最佳实践

### 42.8.1 高频坑

| 坑 | 表现 | 规避 |
| --- | --- | --- |
| **状态机单例共享** | `@EnableStateMachine`（非工厂）全局一个实例，并发订单状态互相污染 | 用 `@EnableStateMachineFactory`，每个业务对象独立实例 |
| **忘了恢复状态** | 直接 new 状态机 sendEvent，永远从 initial 状态流转 | 每次操作前 `resetStateMachine(当前状态)`（42.5.3） |
| **非法事件吞掉异常** | `sendEvent` 返回 false 后业务照常继续 | 检查返回值并抛业务异常/告警 |
| **Guard 里做 IO** | 守卫里查库/调远程，状态流转链路拖慢 | Guard 保持轻量；重操作放 Action |
| **状态与业务表脱节** | 状态机状态和 `t_order.status` 不一致 | 状态即表字段，流转后立刻落库 + 乐观锁 |
| **并发双写** | 两个请求同时流转同一订单，后写覆盖前写 | `UPDATE ... WHERE version = ?` 乐观锁 |
| **过度使用** | CRUD 的 2 个状态也上状态机 | 按 42.7.2 决策；状态少就别上 |
| **版本不匹配** | Boot 2.x 引入 4.x 依赖启动报错 | 3.x 配 Boot 2.x，4.x 配 Boot 3.x |
| **Action 抛异常无补偿** | 支付动作失败后状态已变，对账失败 | 事务包住 Action；失败回滚 + 重试/补偿（结合 16 章 RabbitMQ 可靠投递） |

### 42.8.2 最佳实践 Checklist

```
□ 用 StateMachineFactory，不用全局单例状态机
□ 业务表 status 字段 = 状态机状态，操作前 resetStateMachine 对齐
□ sendEvent 返回值必须检查，非法事件要有业务异常 + 告警
□ Guard 只做轻量判定，Action 做横切动作（发事件/通知/审计）
□ 监听器统一落审计日志（谁在何时触发何事件、被拒的非法尝试）
□ 并发用乐观锁（version）兜底，防双写覆盖
□ 状态图先画在纸上/README 里，再写配置（配置 = 图）
□ 状态少（≤3）不上框架，避免过度设计
□ 状态机不替代领域对象：规则仍在聚合根，状态机管"迁移机制"
```

## 42.9 练习与总结

### 练习题

```java
// 1. 跑通 42.3.4 的"待支付→支付"最小状态机，打印状态变化
// 2. 给订单状态机增加"超时自动关闭"：PENDING --TIMEOUT(定时任务触发)--> CANCELLED，
//    用 18 章 XXL-Job 每分钟扫一次超时订单并 sendEvent(OrderEvents.TIMEOUT)
// 3. 为"审批流"建模：DRAFT → SUBMITTED → APPROVING → APPROVED/REJECTED，
//    配置全部合法迁移，写 5 个测试覆盖"非法迁移被拒"
// 4. 实现 Guard："VIP 用户可跳过风控"（从 extendedState 取用户等级判断）
// 5. （进阶）用 42.5.4 的 StateMachinePersist 方案把状态机快照存库，
//    模拟"支付一半重启"后 restore 继续流转
// 6. 对比你项目中手写 if 的状态流转，评估哪些值得迁移到状态机
```

### 面试高频问题清单

| 问题 | 答案要点 |
| --- | --- |
| 状态机四要素？ | 状态、事件、迁移、动作；加守卫（Guard）共五要素 |
| Spring StateMachine 怎么配置？ | StateMachineConfigurerAdapter 声明 states 与 transitions，用 Factory 获取实例 |
| sendEvent 返回 false 说明什么？ | 事件非法（当前状态无此迁移边）或 Guard 不满足；需检查并抛业务异常 |
| Guard 和 Action 区别？ | Guard=迁移前的条件判断（决定允不允许）；Action=迁移时执行的行为（调支付/发通知） |
| 为什么用 StateMachineFactory？ | 每个业务对象独立状态机实例，避免单例状态污染与并发串扰 |
| 状态机怎么持久化？ | 业务表 status 对齐（主流）+ 每次操作 resetStateMachine 恢复；或 StateMachinePersist 序列化快照 |
| 状态机和状态模式区别？ | 状态模式=行为分派的 OOP 结构；状态机=迁移规则引擎，含 Guard/Action/持久化/审计 |
| 状态机和手写 if 怎么选？ | 状态少用 if；状态多+迁移规则复杂+要审计恢复用状态机（42.7.2 决策） |
| 状态机并发问题？ | 双写覆盖用乐观锁（version 条件更新）；跨实例用分布式锁 |
| 状态机和 DDD 关系？ | 状态机管迁移机制，聚合根管业务规则；状态定义在聚合根，迁移表配置在外层 |

### 本章小结

- **为什么需要状态机**（42.1）：手写 if 的五大痛点（图不可见/非法迁移/无审计），FSM 四要素与"同一状态+同一事件结果确定"铁律；
- **框架概览**（42.2）：Spring StateMachine 能力清单与依赖引入；
- **订单状态机**（42.3）：状态/事件枚举、配置迁移表（宪法）、Guard/Action、事件驱动流转；
- **三种写法与监听器**（42.4）：Lambda/Bean/SpEL 对比，Listener 做审计监控；
- **持久化**（42.5）：状态对齐业务表（主流）与 StateMachinePersist 快照两种方案 + 乐观锁并发；
- **实战**（42.6）：完整订单状态机（5 状态 5 事件）+ 迁移表测试；
- **选型对照**（42.7）：手写 if vs 状态模式 vs Spring StateMachine 决策；
- **坑与实践**（42.8）：单例共享、忘了恢复状态、非法事件吞异常等 9 坑。

配套：第四十章（ensureStatus 手写基线）、第四十一章（聚合内状态、领域事件）、第十四章（状态模式）、第十八章（XXL-Job 定时超时流转）、第十六章（Action 失败可靠投递/补偿）。

---

至此，第 42 章 Spring StateMachine 学习完成。下一章 [43-CQRS与事件溯源.md](./43-CQRS与事件溯源.md)（命令查询分离与事件流存储，状态机的"存储层"终极形态）｜ 返回：[README.md](./README.md)
