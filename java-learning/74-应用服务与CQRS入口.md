# 第七十四章 应用服务与 CQRS 入口（UseCase 编排）

> **本章目标**：把"应用服务（Application Service）"作为分层架构的"用例编排入口"做透，掌握 Command / Query 分离、事务边界、跨聚合事件发布。
>
> **前置知识**：第 73 章（六边形架构）、第 41 章（聚合/仓储/领域服务）、第 43 章（CQRS）、第 46 章（Saga）。

> 一句话：**应用服务不写业务规则，只做"编排 + 事务 + 跨聚合协调"**。

---

## 74.1 应用服务在 DDD 中的位置

应用服务是 DDD 四层中**最薄的一层**——它不写业务规则（那是领域层的事），只做"胶水"：

```
┌─────────────────────────────────────────┐
│ 用户界面层（Controller / CLI / MQ）        │  接收请求、转换为命令
├─────────────────────────────────────────┤
│ 应用层（Application Service / UseCase）    │  编排 + 跨聚合协调 + 事务
├─────────────────────────────────────────┤
│ 领域层（Domain）                          │  业务规则承载
├─────────────────────────────────────────┤
│ 基础设施层（Infrastructure）               │  MyBatis / Redis / MQ 实现
└─────────────────────────────────────────┘
```

应用服务的三大职责：

| 职责 | 示例 |
| --- | --- |
| **用例编排** | "创建订单"= 校验 + 创建聚合 + 持久化 |
| **事务边界** | 一个应用服务方法 = 一个事务单元 |
| **跨聚合协调** | 创建订单 → 触发库存预留 → 触发支付扣款 |

> **绝不允许**应用服务承担"业务规则"。业务规则必须在领域层。

---

## 74.2 UseCase 风格 vs Service 风格

| 维度 | Service 风格 | UseCase 风格 |
| --- | --- | --- |
| 命名 | `OrderAppService` | `SubmitOrderUseCase` |
| 形态 | 具体类 | 接口 + 实现 |
| 方法命名 | 名词/动词（`createOrder`、`payOrder`） | 单一动词（`execute`、`submit`） |
| 入参 | 命令对象 | 命令对象 |
| 调用方 | 直接注入 AppService | 注入 UseCase 接口 |
| 适合 | DDD 四层工程 | 六边形 / 整洁架构工程 |

### 74.2.1 UseCase 风格示例

```java
// 接口
public interface SubmitOrderUseCase {
    Long execute(SubmitOrderCommand cmd);
}

// 实现
@Service
public class SubmitOrderUseCaseImpl implements SubmitOrderUseCase {
    private final OrderRepository repository;
    private final DomainEventPublisher events;

    @Override
    @Transactional
    public Long execute(SubmitOrderCommand cmd) {
        Order order = Order.create(
            OrderId.generate(),
            new CustomerId(cmd.customerId()),
            toItems(cmd.items())
        );
        repository.save(order);
        events.publish(new OrderCreatedEvent(order.id(), Instant.now()));
        return order.id();
    }
}
```

### 74.2.2 一个 UseCase 一个文件 vs 一个 Service 多方法

| 模式 | 优势 | 劣势 |
| --- | --- | --- |
| 一个 UseCase 一个接口 | 单一职责、易测试、易扩展 | 文件多 |
| 一个 Service 多方法 | 文件少、相关用例聚合 | 类臃肿（"上帝服务"） |

> **推荐**：业务规则独立的用例（如 `SubmitOrderUseCase` / `CancelOrderUseCase`）拆开；关联紧密的（如订单的 5 个用例）可合并为 `OrderCommandService`。

---

## 74.3 命令对象（Command）与查询对象（Query）

### 74.3.1 命令对象设计规范

```java
// 命名：动词原形 / 祈使句 + Command
public record SubmitOrderCommand(
    Long customerId,
    List<ItemCommand> items
) {
    public record ItemCommand(
        Long productId,
        String productName,
        int quantity,
        Money unitPrice
    ) {}
}
```

**纪律**：

- 命令不可变（`record` 天然满足）
- 强类型校验（构造器抛异常）
- 不携带数据库 ID（命令是新业务的起点）
- 不携带时间戳（创建时间由领域产生）

### 74.3.2 查询对象（CQRS 读侧）

```java
// 命名：find / get / list + By + 条件
public record FindOrderQuery(
    Long orderId,
    OrderStatus statusFilter,
    Instant createdFrom,
    Instant createdTo,
    int page,
    int size
) {
    public FindOrderQuery {
        if (page < 0) page = 0;
        if (size <= 0 || size > 200) size = 20;
    }
}
```

**纪律**：

- 查询参数必须可序列化（前端能直接 JSON 构造）
- 必填字段显式 `@NotNull`（或不写默认值）
- 分页参数必有 + 默认值

---

## 74.4 事务边界：应用服务方法 = 一个事务单元

### 74.4.1 事务归属原则

**事务只能开在应用服务方法上**，不能在领域层方法、不能在基础设施层。

```java
@Service
public class OrderAppService {

    @Transactional   // ✅ 事务开在应用服务
    public OrderId submit(SubmitOrderCommand cmd) {
        Order order = Order.create(/* ... */);
        repository.save(order);
        return order.id();
    }
}

public class Order {   // ❌ 领域层不能开事务
    public void pay() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException(/* ... */);
        }
    }
}
```

### 74.4.2 多写操作的同一事务

```java
@Transactional
public void submitWithInventory(SubmitOrderCommand cmd) {
    Order order = Order.create(/* ... */);
    orderRepository.save(order);

    Inventory inventory = inventoryRepository.findById(cmd.productId())
        .orElseThrow();
    inventory.reserve(cmd.quantity());          // 预留库存（领域行为）
    inventoryRepository.save(inventory);
    // 两个写操作在同一个事务中，要么都成功、要么都失败
}
```

### 74.4.3 事务传播

| 场景 | 传播行为 |
| --- | --- |
| 读侧调用（统计、报表） | `Propagation.SUPPORTS` 或 `readOnly = true` |
| 写侧调用（创建、支付） | `Propagation.REQUIRED`（默认） |
| 嵌套调用（子方法也开事务） | `Propagation.REQUIRES_NEW`（新事务，挂起外层） |
| 不影响外层事务（异步补偿） | `Propagation.NOT_SUPPORTED` |

---

## 74.5 跨聚合协调：三种选择

### 74.5.1 本地一致性（一个事务）

```java
@Transactional
public void createOrderAndReserveInventory(SubmitOrderCommand cmd) {
    Order order = Order.create(/* ... */);
    orderRepository.save(order);

    Inventory inv = inventoryRepository.findById(cmd.productId()).orElseThrow();
    inv.reserve(cmd.quantity());
    inventoryRepository.save(inv);
    // ✅ 强一致：要么全成功、要么全失败
}
```

**适用**：聚合在同一个限界上下文、同进程、同数据库。

### 74.5.2 最终一致性（领域事件）

```java
@Transactional
public OrderId submit(SubmitOrderCommand cmd) {
    Order order = Order.create(/* ... */);
    repository.save(order);
    events.publish(new OrderCreatedEvent(order.id(), /* ... */));
    return order.id();
}

@EventListener
@Transactional
public void on(OrderCreatedEvent event) {
    Inventory inv = inventoryRepository.findById(/* ... */).orElseThrow();
    inv.reserve(event.getQuantity());
    inventoryRepository.save(inv);
}
```

**适用**：跨聚合但同限界上下文；或上下游能容忍秒级延迟。

### 74.5.3 Saga（跨服务）

```java
@Transactional
public OrderId submit(SubmitOrderCommand cmd) {
    Order order = Order.create(/* ... */);
    repository.save(order);
    sagaManager.start(new PlaceOrderSaga(order.id(), cmd));
    return order.id();
}
```

**适用**：跨限界上下文 / 跨服务 / 跨数据库（如订单-支付-库存三服务）。

### 74.5.4 决策树

```
跨聚合一致性如何选？
│
├── 同一限界上下文、同一聚合根？
│   └── 在聚合根方法内直接操作（无需应用服务）
│
├── 同一限界上下文、跨聚合？
│   ├── 强一致需求 ──► 一个事务（74.5.1）
│   └── 可容忍秒级延迟 ──► 领域事件（74.5.2）
│
└── 跨限界上下文 / 跨服务？
    └── Saga（74.5.3，第 79 章详解）
```

---

## 74.6 领域事件发布：同步 vs Outbox

### 74.6.1 同步发布（进程内）

```java
@Service
public class OrderAppService {
    private final OrderRepository repository;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public OrderId submit(SubmitOrderCommand cmd) {
        Order order = Order.create(/* ... */);
        repository.save(order);
        publisher.publishEvent(new OrderCreatedEvent(order.id()));   // 进程内事件
        return order.id();
    }
}
```

**优点**：实现简单。

**缺点**：

- 事件订阅者在同一事务内，订阅者失败会导致整个事务回滚
- 跨进程无法投递（进程内事件总线）
- 失败重试需自行实现

### 74.6.2 Outbox 模式（跨进程/可靠投递）

详见第 79 章"Outbox 模式"。核心思想：业务事务内同时把事件写入 `outbox_event` 表，由后台调度器异步投递到 MQ。

```java
@Transactional
public OrderId submit(SubmitOrderCommand cmd) {
    Order order = Order.create(/* ... */);
    repository.save(order);

    // 写 outbox 表（与业务同一事务）
    outboxWriter.write(new OrderCreatedEvent(order.id()));
    return order.id();
}
```

---

## 74.7 实战：订单应用服务的 5 个核心用例

```java
@Service
public class OrderAppService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;     // 应用层依赖应用层（同进程）
    private final OrderEventPublisher events;

    public OrderAppService(OrderRepository orderRepository,
                           InventoryService inventoryService,
                           OrderEventPublisher events) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.events = events;
    }

    /** 用例 1：提交订单 */
    @Transactional
    public OrderId submit(SubmitOrderCommand cmd) {
        List<OrderItem> items = cmd.items().stream()
            .map(i -> new OrderItem(i.productId(), i.productName(),
                                    i.quantity(), i.unitPrice()))
            .toList();
        Order order = Order.create(
            OrderId.generate(),
            new CustomerId(cmd.customerId()),
            items
        );
        orderRepository.save(order);
        events.publish(new OrderCreatedEvent(order.id()));
        return order.id();
    }

    /** 用例 2：支付订单 */
    @Transactional
    public void pay(PayOrderCommand cmd) {
        Order order = orderRepository.findById(new OrderId(cmd.orderId()))
            .orElseThrow(() -> new OrderNotFoundException(cmd.orderId()));
        order.pay();
        orderRepository.save(order);
        events.publish(new OrderPaidEvent(order.id()));
    }

    /** 用例 3：取消订单 */
    @Transactional
    public void cancel(CancelOrderCommand cmd) {
        Order order = orderRepository.findById(new OrderId(cmd.orderId()))
            .orElseThrow();
        order.cancel();
        orderRepository.save(order);
        events.publish(new OrderCancelledEvent(order.id()));
    }

    /** 用例 4：发货 */
    @Transactional
    public void ship(ShipOrderCommand cmd) {
        Order order = orderRepository.findById(new OrderId(cmd.orderId()))
            .orElseThrow();
        order.ship();
        orderRepository.save(order);
        events.publish(new OrderShippedEvent(order.id()));
    }

    /** 用例 5：退款 */
    @Transactional
    public void refund(RefundOrderCommand cmd) {
        Order order = orderRepository.findById(new OrderId(cmd.orderId()))
            .orElseThrow();
        order.refund(cmd.reason());
        orderRepository.save(order);
        events.publish(new OrderRefundedEvent(order.id(), cmd.reason()));
    }
}
```

---

## 74.8 CQRS 读侧：QueryService

### 74.8.1 读写分离的核心理由

- 写侧：聚合根 → 充血 + 状态机 + 领域规则 → 慢
- 读侧：直接 SQL 投影 → 快

把它们混在一起会导致：

- 读侧为了性能"绕过"聚合根 → 业务规则被破坏
- 写侧为了完整性"加锁" → 读侧阻塞

### 74.8.2 读侧实现

```java
@Service
public class OrderQueryService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;

    public OrderView findById(Long orderId) {
        OrderPO po = orderMapper.selectById(orderId);
        if (po == null) return null;

        List<OrderItemPO> items = itemMapper.selectByOrderId(orderId);
        return OrderView.builder()
            .orderId(po.getId())
            .status(po.getStatus())
            .total(po.getTotalAmount())
            .items(items.stream().map(this::toItemView).toList())
            .build();
    }

    public Page<OrderView> search(FindOrderQuery query) {
        // 分页查询，直接 SQL 投影，不经过聚合根
        Page<OrderPO> page = orderMapper.selectByStatusAndTime(
            query.statusFilter(),
            query.createdFrom(),
            query.createdTo(),
            PageRequest.of(query.page(), query.size())
        );
        return page.map(this::toView);
    }
}
```

> 读侧**绕过聚合根**，直接走 SQL 投影——这是 CQRS 的核心收益。

---

## 74.9 防腐层（ACL）在应用服务中的落位

### 74.9.1 场景

订单应用需要查询"用户收货地址"——这是另一个限界上下文（用户上下文）。

### 74.9.2 直接调用（反例）

```java
// ❌ 反例：直接在应用服务调用远程 RPC
public OrderId submit(SubmitOrderCommand cmd) {
    UserAddress addr = userClient.getAddress(cmd.customerId());   // 远程 RPC
    // ... 业务逻辑
}
```

**问题**：

- 远程服务挂了，整个事务失败
- 远程响应慢，应用服务阻塞
- 远程返回的字段变化会冲击业务

### 74.9.3 防腐层（ACL）

```java
// 防腐层接口（由调用方拥有）
public interface UserAddressPort {
    Optional<UserAddress> findById(Long userId);
    record UserAddress(Long userId, String province, String city, String detail) {}
}

// 防腐层实现（基础设施层）
@Component
public class UserAddressFeignAdapter implements UserAddressPort {
    private final UserServiceFeignClient feignClient;
    private final CircuitBreakerFactory cb;

    @Override
    @CircuitBreaker(name = "user-service", fallbackMethod = "fallback")
    public Optional<UserAddress> findById(Long userId) {
        UserDTO dto = feignClient.getById(userId);
        return Optional.ofNullable(dto).map(this::toDomain);
    }

    private Optional<UserAddress> fallback(Long userId, Throwable t) {
        log.warn("用户服务降级，返回默认地址 userId={}", userId, t);
        return Optional.empty();
    }
}

// 应用服务注入端口
public class OrderAppService {
    private final UserAddressPort userAddressPort;

    public OrderId submit(SubmitOrderCommand cmd) {
        Optional<UserAddress> addr = userAddressPort.findById(cmd.customerId());
        // ...
    }
}
```

> 防腐层的三大价值：
> 1. **接口归调用方所有**——订单域定义需要什么，用户域怎么实现与我无关
> 2. **降级兜底**——远程挂了有默认值，不影响业务主流程
> 3. **数据转换**——远程 DTO 转自家值对象，业务不被外部字段变化冲击

---

## 74.10 决策树：事务边界 vs 性能

```
应用服务方法的事务边界怎么定？
│
├── 一个用例只写一个聚合？
│   └── 一个 @Transactional 就够（74.4.2）
│
├── 一个用例写多个聚合，但都在同库？
│   └── 一个 @Transactional + 领域事件驱动另一聚合（同库可订阅）
│
├── 一个用例跨多个数据库？
│   ├── 能容忍最终一致 ──► Saga（第 79 章）
│   └── 必须强一致 ──► 评估是否需要分布式事务（2PC / Seata AT）⚠️
│
└── 性能敏感（高并发写入）？
    └── 命令侧与查询侧必须分离（CQRS 74.8）
```

---

### 本章小结

- 应用服务三大职责：用例编排 + 事务边界 + 跨聚合协调。
- 业务规则永远不进应用服务。
- UseCase 风格（接口 + 实现）适合六边形/整洁，Service 风格适合 DDD 四层。
- 命令/查询对象必须强类型 + 不可变。
- 跨聚合一致性三选一：本地事务 / 领域事件 / Saga。
- 防腐层是应用层与外部限界上下文交互的标准模式。

### 面试高频问题清单（74 章）

1. 应用服务的三大职责是什么？
2. 为什么事务只能开在应用层？
3. UseCase 风格与 Service 风格的区别？
4. 跨聚合一致性的三种选择？
5. 命令对象的设计纪律有哪些？
6. CQRS 读侧为什么"绕过"聚合根？
7. 防腐层的三大价值是什么？
8. @Transactional 的传播行为有哪些？各适用什么场景？

### 练习题

1. 把现有 OrderService 改造为"实现 UseCase 接口"风格。
2. 设计 SubmitOrderCommand / PayOrderCommand / CancelOrderCommand 三个命令对象。
3. 写一个 OrderQueryService 实现"按状态分页查询"，绕过聚合根直接 SQL。
4. 为订单域写 UserAddressPort 防腐层接口与 Feign 实现。
5. 用 ArchUnit 写 1 条规则：application 包不依赖 interfaces 包。

### 配套

- **前置**：第 73 章（六边形架构）、第 43 章（CQRS 与事件溯源）、第 46 章（分布式事务与 Saga）
- **后续**：第 75 章（Modular Monolith 落地）

---
至此，第七十四章 应用服务与 CQRS 入口 学习完成。下一章 [75-ModularMonolith落地.md](./75-ModularMonolith落地.md) ｜ 返回：[README.md](./README.md)