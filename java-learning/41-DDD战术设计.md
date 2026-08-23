# 第四十一章 DDD 战术设计（实体 / 值对象 / 聚合 / 仓储实践）

> 本章目标：在第四十章"贫血模型与充血模型"基础上，系统掌握 DDD 战术设计（Tactical Design）的四大核心构件——**实体（Entity）、值对象（Value Object）、聚合与聚合根（Aggregate/AggregateRoot）、仓储（Repository）**，理解它们各自的职责、特征与建模方法，会用 Java 完整实现一个"订单聚合"（订单 + 订单项 + 仓储 + 应用服务），并掌握限界上下文、领域服务、工厂、规格模式等配套概念，能独立完成一个核心业务域的 DDD 建模。
>
> 前置知识：第四十章 贫血模型与充血模型（充血模型是战术设计的基础）、第二章 面向对象编程、第二十八章 设计原则深度（依赖倒置）、第十七章 MyBatis-Plus / 第九章 Spring Boot（仓储实现载体）、第三十一章 商城实战（改造对象）。

## 41.1 从"充血模型"到"DDD 战术设计"

### 41.1.1 第 40 章我们走到了哪一步

第四十章把订单的"业务规则"从 Service 下沉到了 `Order` 实体：

```java
public class Order {
    public void pay() { ensureStatus(PENDING, "支付"); this.status = PAID; }
    public void cancel() { ensureStatus(PENDING, "取消"); this.status = CANCELLED; }
}
```

这解决了"**规则放哪**"的问题。但真实业务远不止一个孤立实体：

- 订单下还有**订单项**（OrderItem），它们和订单是一个整体吗？
- 支付时还要查**账户**、扣**库存**，跨对象规则放哪？
- 实体怎么"取出来"和"存回去"？直接依赖 Mapper 吗？
- 两个微服务都叫"订单"，业务一样吗？

这些问题就是 **DDD 战术设计**要回答的。战术设计是"**用一组建模模式把领域模型落地成可运行代码**"的方法。

### 41.1.2 DDD 全景：战略设计 vs 战术设计

| 层面 | 解决的问题 | 核心内容 |
| --- | --- | --- |
| **战略设计（Strategic）** | "系统该怎么切分？各模块边界在哪？" | 限界上下文（Bounded Context）、上下文映射、通用语言（Ubiquitous Language） |
| **战术设计（Tactical）** | "一个上下文内部，代码怎么组织？" | 实体、值对象、聚合、领域服务、仓储、工厂、规格模式 |

> **一句话记忆**：战略设计画"**地图**"（系统分成几块、块与块什么关系），战术设计画"**施工图**"（每一块内部怎么建模）。本章讲施工图，但开头先给你地图的比例尺（41.2）。

### 41.1.3 战术设计的整体布局（先看全貌）

```
┌────────────────────────── 限界上下文（如：订单上下文） ──────────────────────────┐
│                                                                              │
│  ┌──────────────────── 领域模型（充血，纯内存，不依赖 Spring） ────────────────┐  │
│  │                                                                          │  │
│  │  实体 Entity          值对象 Value Object       聚合根 Aggregate Root    │  │
│  │  (Order)              (Money / Address)         (Order 是整个聚合的入口)  │  │
│  │                                                                          │  │
│  │  领域服务 Domain Service（跨对象规则，如：支付对账 PaymentService）         │  │
│  │  工厂 Factory（聚合的复杂创建）   规格 Specification（复杂判定）           │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│             ▲ 依赖倒置：领域层定义接口，不依赖任何实现                        │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │  仓储 Repository 接口（OrderRepository）  ← 领域层定义                   │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│             ▼ 实现                                                           │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │  基础设施层：JpaOrderRepository / MyBatisOrderRepository + Mapper        │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  应用服务 Application Service（OrderAppService）：编排用例、开事务、调仓储      │
└──────────────────────────────────────────────────────────────────────────────┘
```

> 核心思想：**领域层最纯粹（只表达业务）**，基础设施层（数据库、框架）通过接口倒置接入。这就是第二十八章讲的依赖倒置（DIP）在 DDD 中的标准落法。

## 41.2 战略设计速览：限界上下文与通用语言

> 战术设计不是空中楼阁，它发生在"限界上下文"内部。先花一小节把战略基础补上，否则后面讨论"聚合边界"时会糊涂。

### 41.2.1 限界上下文（Bounded Context）

**概念**：一个限界上下文是一个**有清晰业务边界**的子系统，边界内使用**同一套领域模型与通用语言**，边界外各自为政。

**经典例子**：电商系统里"订单"出现在三个上下文，含义完全不同——

| 上下文 | "订单"指什么 | 关注的字段 |
| --- | --- | --- |
| **销售上下文**（用户下单） | 用户要买的商品组合 | 商品、数量、金额、优惠 |
| **物流上下文**（发货） | 一个待配送的包裹 | 地址、重量、收件人、运单号 |
| **财务上下文**（对账） | 一笔应收款凭证 | 支付流水、税率、发票 |

三个上下文里的"Order"模型**不需要统一**，各自建模、各自演化，通过**上下文映射**（防腐层/开放主机服务等）对接。这解决了"一个实体全公司到处用，改一处崩一片"的经典难题。

### 41.2.2 通用语言（Ubiquitous Language）

**概念**：业务专家和开发团队使用**同一套术语**，且术语直接映射到代码命名。

```
业务专家说："订单可以支付、取消、退款，支付后才能发货"
    ↓ 通用语言
代码里：Order.pay() / Order.cancel() / Order.refund() / Order.ship()
  而不是：OrderService.handleOrder(OrderHandleDTO dto, Integer type)
```

> **判断标准**：如果代码里出现"处理订单""修改状态"这种"翻译腔"命名，说明通用语言没建立好。**领域术语直接进代码**是 DDD 的最低要求。

### 41.2.3 战略设计产物 → 战术设计的输入

战略设计产出：**上下文集 + 每个上下文内的领域模型草图**。本章接下来的所有模式（实体/值对象/聚合/仓储），都用于实现**一个上下文内部**的领域模型。

## 41.3 实体（Entity）

### 41.3.1 定义与特征

**实体**是有**唯一标识**、有**生命周期**、状态**可变**的领域对象。

| 特征 | 说明 | Java 落法 |
| --- | --- | --- |
| 唯一标识（Identity） | 靠 id 区分两个对象，即使字段全相同也是不同个体 | `Long id` / 业务主键 `orderNo` |
| 生命周期 | 创建 → 状态流转 → 可能被删除/归档 | 构造器 + 行为方法 + 状态字段 |
| 可变（Mutable） | 业务行为会修改自身状态 | `pay()` 内部 `this.status = PAID` |
| 有行为 | 承载业务规则（充血模型） | 行为方法内聚 |

```java
// 实体示例：两个"张三"姓名相同，但 id 不同就是不同的人
public class Customer {
    private Long id;                 // 唯一标识
    private String name;
    private Address homeAddress;     // 属性可以是值对象（见 41.4）

    public Customer(Long id, String name) { this.id = id; this.name = name; }

    public void changeAddress(Address newAddress) { this.homeAddress = newAddress; }

    @Override
    public boolean equals(Object o) {
        // 实体相等 = 标识相等（不是字段全等！）
        return o instanceof Customer other && id.equals(other.id);
    }
    @Override
    public int hashCode() { return id.hashCode(); }
}
```

### 41.3.2 实体的 equals 为什么必须按 id

| 场景 | 按 id 判断 | 按字段全比 |
| --- | --- | --- |
| 两次查库返回同一订单 | 相等 ✅ | 可能相等（字段相同） |
| 订单支付后 vs 支付前 | 同一对象，相等 ✅ | **不相等**（状态变了）❌ |
| 集合去重 | 正确 ✅ | 状态一变就重复 ❌ |

> **铁律**：实体的 `equals/hashCode` **只看标识**（id/业务主键），不看业务字段。否则对象一旦发生状态流转，在集合里的身份就"变"了。

## 41.4 值对象（Value Object）

### 41.4.1 定义与特征

**值对象**是**没有标识**、**不可变**、靠**属性值相等**来判断同一性的领域对象。它是 DDD 中"被低估但极重要"的模式。

| 特征 | 说明 | Java 落法 |
| --- | --- | --- |
| 无标识 | 没有 id，"500 元"就是"500 元"，无需编号 | 无 id 字段 |
| 不可变（Immutable） | 创建后不可修改，修改=创建新对象 | `final` 字段 + 无 setter |
| 值相等 | 两个对象属性相同即相等 | 重写 equals/hashCode 全字段比较 |
| 自带行为 | 领域计算放在值对象里（如金额加减、币种换算） | `Money.add(Money)` |

```java
// 值对象：金额（金额 + 币种 + 运算规则内聚）
public final class Money {                 // final：不可继承
    private final BigDecimal amount;       // final：不可变
    private final String currency;         // 币种

    public Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);   // 返回新对象
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        if (amount.compareTo(other.amount) < 0) {
            throw new IllegalArgumentException("余额不足");
        }
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(int times) { return new Money(amount.multiply(BigDecimal.valueOf(times)), currency); }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) throw new IllegalArgumentException("币种不一致");
    }

    @Override
    public boolean equals(Object o) {        // 值对象：全字段相等
        return o instanceof Money m
                && amount.compareTo(m.amount) == 0 && currency.equals(m.currency);
    }
    @Override
    public int hashCode() { return amount.stripTrailingZeros().hashCode() * 31 + currency.hashCode(); }
    // getter（无 setter）...
}
```

> **Java 17 最佳实践**：值对象直接用 `record` 完美契合（不可变 + 自动 equals/hashCode/toString + 解构）。`public record Money(BigDecimal amount, String currency) {}` 再补充行为方法即可。但注意 record 的字段是 `final`，行为方法只能"返回新对象"——这正是值对象的要求。

### 41.4.2 常见值对象清单

| 值对象 | 为什么是值对象（而非实体） | 典型行为 |
| --- | --- | --- |
| `Money` | 金额本身无需 id | 加减乘、币种换算、比较 |
| `Address` | 地址相同即相同 | 合法性校验、格式化 |
| `PhoneNumber` | 号码相同即相同 | 校验、脱敏 |
| `OrderItem` 明细（局部标识） | 在聚合内由"行号/商品"标识即可 | 小计计算 |
| `DateRange` | 时间段相同即相同 | 重叠判断、包含判断 |
| `Email` | 邮箱相同即相同 | 校验、域名提取 |

### 41.4.3 实体 vs 值对象：怎么判断

一个"判断口诀"（经典测试）：**两个对象交换后，系统还一样吗？**

- 交换后系统不变 → **值对象**（把钱从 A 订单挪到 B 订单，系统毫无差别）；
- 交换后系统变了 → **实体**（把 A 的客户换成 B 的客户，天差地别）。

| 判断维度 | 实体 | 值对象 |
| --- | --- | --- |
| 有唯一标识？ | ✅ | ❌ |
| 可变？ | ✅（状态流转） | ❌（不可变） |
| 相等判断 | 按 id | 按属性 |
| 生命周期 | 有 | 无（随宿主生灭） |
| 例子 | 订单、用户、商品 | 金额、地址、邮箱、时间段 |

## 41.5 聚合与聚合根（本章核心）

### 41.5.1 为什么需要聚合

直接让实体"自由关联"会出问题：

```
❌ 错误示范：实体互相引用、谁都能改谁
Order ←→ Customer ←→ Address ←→ ...（引用网）
任意对象都能 new 一个 OrderItem 塞进订单、直接改订单状态
→ 一致性靠"每个人自觉"，必然漏
```

**聚合（Aggregate）** 的解法：把**一组必须保持一致性的对象**打包成一个整体，规定：

1. **对外只暴露聚合根**（Aggregate Root），外部操作聚合**只能通过聚合根**；
2. **内部对象**（如 OrderItem）只能通过聚合根访问/修改，外界拿不到它们的引用（或拿到了也不能直接改）；
3. **一个事务只修改一个聚合**（跨聚合修改是分布式事务/最终一致性的问题）；
4. **聚合内一致性由聚合根保证**（如"订单总价 = 所有订单项小计之和"在 Order 内部保证）。

### 41.5.2 订单聚合示例

```java
// 聚合根：Order（整个聚合的唯一入口）
public class Order {
    private OrderId id;                       // 实体标识
    private CustomerId customerId;            // 只存"别的聚合的 id"，不持有别的聚合对象！
    private OrderStatus status;
    private List<OrderItem> items;            // 内部实体（受聚合根管理）
    private Money totalAmount;

    // 内部实体：OrderItem（不能是公开 new 的独立对象）
    static class OrderItem {
        private ProductId productId;
        private int quantity;
        private Money unitPrice;

        OrderItem(ProductId productId, int quantity, Money unitPrice) {
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        Money subtotal() { return unitPrice.multiply(quantity); }
        // 包内可见，不给 setter——只能被 Order 修改
    }

    // ── 聚合根对外行为：所有操作都经过这里 ──
    public void addItem(ProductId productId, int quantity, Money unitPrice) {
        // 已支付/已取消的订单不允许加商品
        if (status != OrderStatus.PENDING) throw new IllegalStateException("订单当前状态不允许修改商品");
        items.add(new OrderItem(productId, quantity, unitPrice));
        recalcTotal();
    }

    public void removeItem(ProductId productId) {
        if (status != OrderStatus.PENDING) throw new IllegalStateException("订单当前状态不允许修改商品");
        items.removeIf(i -> i.productId.equals(productId));
        recalcTotal();
    }

    public void submit() {                     // 提交订单：校验后锁定
        if (items.isEmpty()) throw new IllegalStateException("订单不能为空");
        ensureStatus(OrderStatus.PENDING, "提交");
        this.status = OrderStatus.SUBMITTED;
    }

    public void pay() { ensureStatus(OrderStatus.SUBMITTED, "支付"); this.status = OrderStatus.PAID; }

    private void recalcTotal() {
        // 不变量：总价恒等于明细之和——在聚合根内部保证，外部永远无法破坏
        this.totalAmount = items.stream().map(OrderItem::subtotal)
                .reduce(Money.zero(), Money::add);
    }

    private void ensureStatus(OrderStatus expected, String action) {
        if (status != expected) throw new IllegalStateException("当前状态不允许" + action);
    }

    // 只暴露"只读视图"，不暴露内部可变集合的引用！
    public List<OrderItem> getItems() { return List.copyOf(items); }
}
```

### 41.5.3 聚合设计五条铁律

| # | 铁律 | 原因 | 错误示范 |
| --- | --- | --- | --- |
| 1 | 聚合根持有**其他聚合的 id**，不持有对象 | 避免跨聚合随意修改 | `Order` 里塞 `Customer customer` 对象 |
| 2 | 内部对象**只能经聚合根**访问修改 | 保证一致性边界 | 把 `items` 用 `getItems()` 返回可变 list，外部 `order.getItems().add(...)` |
| 3 | **一个事务一个聚合** | 跨聚合事务 = 分布式一致性难题 | 下单事务里同时锁账户+锁库存+改订单 |
| 4 | 聚合根负责**不变量** | 一致性由边界内保证 | 外部手改 `totalAmount` |
| 5 | 聚合尽量**小** | 聚合大 = 并发冲突多 + 一致性负担重 | 把"用户+他的全部订单"放一个聚合 |

> **为什么铁律 1 重要**：`Order` 持有 `Customer` 对象意味着"改客户"必须经过订单聚合，跨聚合的一致性和事务边界瞬间失控。只存 `customerId`，需要客户信息时**通过仓储/查询服务去取**（读模型，见第 43 章 CQRS）。

### 41.5.4 事务边界与并发控制

- **一个聚合 = 一个事务边界**：`@Transactional` 作用在应用服务方法上，方法内只操作一个聚合（取聚合根 → 调行为 → 仓储保存）；
- **并发冲突**：两个请求同时改同一聚合 → 用**乐观锁**（聚合根加 `version` 字段，保存时 `WHERE version = ?`）或悲观锁。聚合越小，冲突概率越低；
- **跨聚合的最终一致性**：通过**领域事件**（第 43 章 Event Sourcing 的兄弟概念）异步协调——下单成功后发 `OrderCreated` 事件，扣库存服务订阅它去扣，而不是在事务里直接调。

## 41.6 领域服务（Domain Service）与工厂（Factory）

### 41.6.1 领域服务：不属于任何单一实体的规则

有些规则**跨多个对象**或**依赖外部查询结果**，塞进任何一个实体都不合适——这时用**领域服务**：

```java
// 领域服务：转账涉及两个账户，不属于任何一个 Account
@Service
public class TransferService {
    private final AccountRepository accountRepository;

    public void transfer(AccountId fromId, AccountId toId, Money amount) {
        Account from = accountRepository.findById(fromId);    // 查数据走仓储
        Account to = accountRepository.findById(toId);
        from.debit(amount);    // 借：扣款（Account 自己的行为）
        to.credit(amount);     // 贷：入账（Account 自己的行为）
        accountRepository.save(from);
        accountRepository.save(to);
    }
}
```

**判定方法**：问自己"这个规则是哪个对象'自己该知道'的事？"——谁都不完全属于 → 领域服务。

### 41.6.2 领域服务 vs 应用服务（区别必须说清）

| 对比 | 领域服务 | 应用服务（Application Service） |
| --- | --- | --- |
| 层级 | 领域层（业务规则） | 应用层（用例编排） |
| 职责 | 跨对象的领域规则 | 开事务、调仓储、发事件、参数校验、权限 |
| 是否含业务规则 | 是（核心业务） | 否（不含领域规则） |
| 示例 | `TransferService.transfer()` | `OrderAppService.submitOrder(SubmitCmd cmd)` |
| 贫血/充血 | 充血（表达规则） | 允许贫血（只是胶水） |

> 三层架构的"Service"在 DDD 里被拆成**应用服务 + 领域服务**两层——这是"伪 DDD 项目"最常犯的错：只改个名，Service 依然是那个几百行的上帝类。

### 41.6.3 工厂（Factory）：聚合的复杂创建

聚合根构造可能很复杂（校验 + 赋初值 + 发创建事件）。简单用构造器/静态工厂，复杂用**工厂方法或工厂类**：

```java
// 静态工厂：把"创建订单"的完整规则收敛一处
public class OrderFactory {
    public static Order create(CustomerId customerId, List<OrderItemDraft> drafts, Money zero) {
        if (drafts == null || drafts.isEmpty()) throw new IllegalArgumentException("订单不能为空");
        Order order = new Order(new OrderId(UUID.randomUUID().toString()), customerId);
        drafts.forEach(d -> order.addItem(d.productId(), d.quantity(), d.unitPrice()));
        order.submit();
        return order;
    }
}
```

## 41.7 仓储（Repository）：让领域层"忘记"数据库

### 41.7.1 为什么必须抽象出仓储

- 实体行为是纯内存的（第 40 章铁律），**谁负责把实体从数据库取出来/存回去**？
- 如果领域服务直接调 `OrderMapper`，领域层就依赖了 MyBatis → 违反依赖倒置；
- **仓储（Repository）** 是领域层的"**对象仓库**"抽象：领域层只认识 `OrderRepository.findById(id)`，不关心背后是 MySQL、Mongo 还是内存。

### 41.7.2 仓储 vs Mapper/DAO（区别）

| 对比 | Repository（仓储） | Mapper/DAO（数据访问） |
| --- | --- | --- |
| 语义 | **对象集合**（取/存的是聚合根） | 表操作（增删改查一行行数据） |
| 粒度 | 一个聚合根一个仓储 | 一张表一个 Mapper |
| 返回 | 领域对象（聚合根） | 实体/记录 |
| 接口定义位置 | **领域层**（倒置） | 基础设施层 |
| 例子 | `orderRepository.findById(id)` | `orderMapper.selectById(id)` |

> 很多项目"MyBatis-Plus 的 Mapper 就是仓储"——**不完全对**。Mapper 是仓储的**基础设施实现**；真正 DDD 要求的是：领域层定义 `OrderRepository` 接口，基础设施层用 Mapper 实现它（41.7.4 给出完整落法）。

### 41.7.3 仓储接口设计规范

```java
// 领域层定义（不 import 任何 MyBatis/JPA 的东西！）
public interface OrderRepository {
    Order findById(OrderId id);                 // 按 id 取聚合根
    void save(Order order);                     // 保存聚合根（新增或更新）
    Optional<Order> findByOrderNo(String orderNo);
    // 注意：不要写 selectPage/selectList 这类"查询工具"，那是查询模型（读侧）的职责
}
```

### 41.7.4 两种主流实现（MyBatis 风格 / Spring Data JPA 风格）

**方案 A：MyBatis-Plus 实现仓储（国内主流）**

```java
// 基础设施层
@Repository
public class MybatisOrderRepository implements OrderRepository {
    private final OrderMapper orderMapper;      // 表映射
    private final OrderItemMapper itemMapper;

    @Override
    public Order findById(OrderId id) {
        OrderPO po = orderMapper.selectById(id.value());          // PO：持久化对象
        if (po == null) return null;
        List<OrderItemPO> itemPos = itemMapper.selectList(
                new LambdaQueryWrapper<OrderItemPO>().eq(OrderItemPO::getOrderId, id.value()));
        return OrderAssembler.toDomain(po, itemPos);              // 装配器：PO → 领域对象
    }

    @Override
    public void save(Order order) {
        OrderPO po = OrderAssembler.toPO(order);
        orderMapper.insertOrUpdate(po);                           // 幂等保存
        // 明细增量同步（删除重建 or 比对 diff）
        itemMapper.delete(new LambdaQueryWrapper<OrderItemPO>().eq(OrderItemPO::getOrderId, order.id().value()));
        OrderAssembler.toItemPos(order).forEach(itemMapper::insert);
    }
}
```

**方案 B：Spring Data JPA 实现（DDD 教科书风格）**

```java
// 直接让 JpaRepository 承担仓储：实体即领域对象（@Entity 注解领域对象）
public interface OrderRepository extends JpaRepository<Order, OrderId> {
    Optional<Order> findByOrderNo(String orderNo);
}
// Order 实体直接 @Entity @Table(name = "t_order")，行为方法照旧，
// JPA 的"实体状态管理"自动完成脏检查保存——OrderRepository.save(order) 即可
```

| 对比 | 方案 A（MyBatis） | 方案 B（Spring Data JPA） |
| --- | --- | --- |
| 领域对象纯净度 | 高（PO 与领域对象分离，两层转换） | 低（领域对象上要标 @Entity 注解） |
| 保存方式 | 手动 diff / 删除重建 | 持久化上下文自动脏检查 |
| 学习曲线 | 平缓（国内主流） | 陡（JPA 状态管理复杂） |
| 适用团队 | 国内企业主流 | 教科书/国外团队风格 |

> **装配器（Assembler）**：PO ↔ 领域对象互转的组件，可配合第 10 章 MapStruct 自动生成。注意它是**基础设施层的帮手**，不是领域层的东西。

## 41.8 实战：完整实现"订单聚合"（DDD 四层落法）

> 以下把第 31 章商城订单模块按 DDD 战术设计完整重写。项目结构即 41.1.3 的布局。

### 41.8.1 包结构（标准 DDD 分层）

```
com.mall.order/
├── order/                          # 限界上下文：订单
│   ├── domain/                     # 领域层（不依赖 Spring/MyBatis）
│   │   ├── model/                  # 实体、值对象、聚合
│   │   │   ├── Order.java          # 聚合根（41.5.2）
│   │   │   ├── OrderItem.java
│   │   │   ├── OrderStatus.java    # 枚举
│   │   │   ├── OrderId.java        # 标识（值对象包装）
│   │   │   └── Money.java          # 值对象（41.4.1）
│   │   ├── repository/
│   │   │   └── OrderRepository.java  # 仓储接口（41.7.3）
│   │   ├── service/
│   │   │   └── TransferService.java  # 领域服务（跨对象规则）
│   │   └── factory/
│   │       └── OrderFactory.java     # 工厂（41.6.3）
│   ├── application/                # 应用层（用例编排）
│   │   ├── cmd/                    # 命令对象（入参）
│   │   │   └── SubmitOrderCmd.java
│   │   └── OrderAppService.java    # 应用服务（事务/编排）
│   ├── infrastructure/             # 基础设施层
│   │   ├── persistence/
│   │   │   ├── OrderPO.java        # 持久化对象
│   │   │   ├── OrderAssembler.java # PO ↔ 领域对象 转换
│   │   │   └── MybatisOrderRepository.java  # 仓储实现
│   │   └── mapper/                 # MyBatis Mapper
│   │       ├── OrderMapper.java
│   │       └── OrderItemMapper.java
│   └── interfaces/                 # 接口层
│       ├── OrderController.java    # REST 接口
│       └── dto/                    # 请求/响应 DTO
```

### 41.8.2 应用服务（用例编排，事务边界）

```java
@Service
public class OrderAppService {
    private final OrderRepository orderRepository;
    private final OrderFactory orderFactory;

    @Transactional(rollbackFor = Exception.class)   // 一个用例 = 一个事务 = 一个聚合
    public OrderId submit(SubmitOrderCmd cmd) {
        Order order = orderFactory.create(cmd.customerId(), cmd.items(), Money.zero());
        orderRepository.save(order);
        return order.id();
    }

    @Transactional
    public void pay(OrderId orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) throw new BusinessException(404, "订单不存在");
        order.pay();                                // 领域行为
        orderRepository.save(order);                // 仓储落库
    }

    @Transactional
    public void addItem(OrderId orderId, OrderItemDraft draft) {
        Order order = orderRepository.findById(orderId);
        order.addItem(draft.productId(), draft.quantity(), draft.unitPrice());
        orderRepository.save(order);
    }
}
```

### 41.8.3 领域层完整代码（Order 聚合 + Money + 仓储接口）

```java
// Money.java —— 值对象（41.4.1 完整版，此处节选）
public final class Money {
    private final BigDecimal amount;
    private final String currency;
    public Money(BigDecimal amount, String currency) { ... }
    public Money add(Money other) { ... }
    public Money subtract(Money other) { ... }
    public Money multiply(int times) { ... }
    public static Money zero() { return new Money(BigDecimal.ZERO, "CNY"); }
    // equals/hashCode 全字段
}

// OrderId.java —— 标识也用值对象包装（防止 Long 满天飞分不清"订单 id"还是"商品 id"）
public record OrderId(String value) {}

// OrderStatus.java —— 状态枚举（状态机边界）
public enum OrderStatus { PENDING, SUBMITTED, PAID, CANCELLED }

// OrderRepository.java —— 领域层接口（不 import 任何基础设施）
public interface OrderRepository {
    Order findById(OrderId id);
    void save(Order order);
}
```

### 41.8.4 验证：领域层单元测试（纯内存、秒跑）

```java
class OrderTest {
    @Test
    void 加两个商品_总价等于小计之和() {
        Order order = new Order(new OrderId("1"), new CustomerId("c1"));
        order.addItem(new ProductId("p1"), 2, new Money(new BigDecimal("10.00"), "CNY"));
        order.addItem(new ProductId("p2"), 1, new Money(new BigDecimal("5.00"), "CNY"));
        assertEquals(new Money(new BigDecimal("25.00"), "CNY"), order.total());
    }

    @Test
    void 已支付订单_禁止加商品() {
        Order order = OrderFactory.create(new CustomerId("c1"),
                List.of(new OrderItemDraft(new ProductId("p1"), 1, Money.zero())), Money.zero());
        order.pay();
        assertThrows(IllegalStateException.class,
                () -> order.addItem(new ProductId("p2"), 1, Money.zero()));
    }

    @Test
    void 空订单_不能提交() {
        Order order = new Order(new OrderId("1"), new CustomerId("c1"));
        assertThrows(IllegalStateException.class, order::submit);
    }
}
```

> 这就是 DDD 战术设计的最大红利：**领域规则不用起 Spring 容器就能测**，测试速度从"秒"级降到"毫秒"级，CI 里跑几百个领域测试毫无压力。

## 41.9 进阶战术模式：规格（Specification）与防腐层

### 41.9.1 规格模式（Specification）

把"复杂业务判定"封装成可组合的对象：

```java
// 规格：用于封装"是否可以支付"这类复杂判定（可复用、可组合）
public interface Specification<T> {
    boolean isSatisfiedBy(T candidate);
    default Specification<T> and(Specification<T> other) {
        return t -> isSatisfiedBy(t) && other.isSatisfiedBy(t);
    }
}

public class OrderPayableSpec implements Specification<Order> {
    @Override
    public boolean isSatisfiedBy(Order order) {
        return order.status() == OrderStatus.SUBMITTED && order.total().amount().signum() > 0;
    }
}

// 用法：在行为方法或应用服务中组合判定
Specification<Order> payable = new OrderPayableSpec()
        .and(o -> !o.isBlocked());       // 组合：可支付 且 未被风控冻结
```

适用场景：判定规则复杂、需要复用的地方（如风控、优惠资格判断）。**简单 if 别硬套规格**，避免过度设计。

### 41.9.2 防腐层（Anti-Corruption Layer，战略级战术组件）

当一个上下文要调用另一个上下文时，在**消费者侧**加一层翻译（防腐层），防止对方的模型污染自己：

```java
// 订单上下文需要"扣库存"，调用库存上下文——用防腐层隔离
@Component
public class StockServiceFacade {            // 防腐层：翻译 + 降级 + 容错
    private final StockApiClient stockClient; // 远程/消息客户端

    public void reserve(List<StockLine> lines) {
        try {
            stockClient.reserve(mapToStockDto(lines));   // 领域对象 → 对方DTO
        } catch (TimeoutException e) {
            // 超时降级：异步补偿 or 人工介入（不让对方模型/故障穿透进来）
        }
    }
}
```

## 41.10 常见坑与最佳实践

### 41.10.1 高频坑

| 坑 | 表现 | 规避 |
| --- | --- | --- |
| **聚合根持有其他聚合对象** | `Order` 里塞 `Customer`/`Account` 对象 | 只存 id；需要时经仓储/防腐层查询 |
| **getItems() 返回可变集合** | 外部 `order.getItems().add(...)` 绕过根修改 | 返回 `List.copyOf` / 不可变视图 |
| **一个事务改多个聚合** | 下单事务里同时改订单+账户+库存 | 一个事务一个聚合；跨聚合用领域事件+最终一致 |
| **仓储接口定义在基础设施层** | `OrderRepository` 和 Mapper 放一起 | 接口放领域层，实现放基础设施层 |
| **Mapper 当仓储用** | Service 直接注入 OrderMapper | 领域层依赖 `OrderRepository`，Mapper 只是实现细节 |
| **实体用 @Data** | setter 全开，聚合不变量被破坏 | 构造器 + 行为方法 + 必要 getter |
| **领域对象标满注解** | @Entity + @Table + MyBatis 注解糊在领域对象上 | MyBatis 方案用 PO 分离；JPA 方案允许 @Entity |
| **值对象可变** | Money 提供 setAmount | 值对象 final + 不可变 + 返回新对象 |
| **伪 DDD：只改名不改造** | Service 改叫 AppService，实体还是贫血 | 规则真正下沉 + 聚合 + 仓储倒置三件套缺一不可 |
| **聚合过大** | 用户+订单+地址全塞一个聚合 | 聚合保持小，宁可多几个聚合 |

### 41.10.2 最佳实践 Checklist

```
□ 每个核心业务域先画"限界上下文"边界，再进战术设计
□ 术语直接进代码（通用语言），不搞"翻译腔"命名
□ 实体：id 判等 + 行为方法 + 不依赖基础设施
□ 值对象：final 不可变 + 值相等 + 行为内聚（Money/Address/Phone）
□ 聚合：小而内聚、单事务单聚合、只经聚合根改内部、跨聚合只存 id
□ 仓储：接口在领域层、实现在基础设施层、以聚合根为单位存取
□ 领域服务：跨对象规则；应用服务：事务与编排；两层职责分清
□ 领域层单测纯内存可跑（不启动 Spring）
□ 复杂度不高的模块（CRUD）保持贫血模型，不为 DDD 而 DDD
```

## 41.11 练习与总结

### 练习题

```java
// 1. 用 record 实现 Money 值对象（含 add/subtract/multiply/compareTo），
//    验证：m1.add(m2) 不修改 m1，返回新对象；两个"10元CNY"相等
// 2. 给 41.5.2 的 Order 聚合增加"部分退款"：状态增加 REFUNDED/PARTIAL_REFUNDED，
//    实现 refund(BigDecimal amount)，规则：仅 PAID/PARTIAL_REFUNDED 可退，累计退款 ≤ 总价
// 3. 把 41.8.4 的三个测试跑通，再新增"订单总价不允许为负"的测试（引入规格或直接在 addItem 校验）
// 4. 用 MyBatis-Plus 实现 OrderRepository（PO + Assembler + Mapper），
//    验证领域层代码一行不改，只换基础设施实现（体现依赖倒置）
// 5. 分析你当前项目：找出 3 个"应该属于同一聚合却被拆开"或"聚合过大"的例子并说明理由
// 6. （进阶）在 OrderAppService.submit 中发布 OrderCreated 领域事件，
//    订阅方模拟扣库存（最终一致性），验证"一个事务一个聚合"的边界
```

### 面试高频问题清单

| 问题 | 答案要点 |
| --- | --- |
| DDD 战略设计与战术设计区别？ | 战略=系统切分（限界上下文/上下文映射）；战术=上下文内建模（实体/值对象/聚合/仓储） |
| 实体和值对象怎么区分？ | 有无标识、可变性、相等依据；"交换后系统变不变"测试法 |
| 什么是聚合根？ | 聚合的唯一入口，外部只能经它访问聚合内部，负责不变量 |
| 聚合设计要点？ | 小而内聚、单事务单聚合、跨聚合只存 id、内部对象不对外暴露可变引用 |
| 为什么一个事务只改一个聚合？ | 跨聚合事务=分布式一致性问题（分布式锁/两阶段提交成本高），用事件+最终一致 |
| 仓储和 Mapper 的区别？ | 仓储=领域层对象集合抽象（接口在领域）；Mapper=基础设施表操作（实现细节） |
| 领域服务和应用服务的区别？ | 领域服务=跨对象业务规则；应用服务=用例编排/事务/调仓储，不含领域规则 |
| 怎么判断一个规则放实体还是领域服务？ | "这个规则属于哪个对象自己"——属于单个对象进实体，跨对象/需外部查询进领域服务 |
| 值对象为什么不可变？ | 无标识对象可变会导致"同一值不同身份"混乱；不可变可安全共享、线程安全 |
| 为什么 Order 只存 customerId 不存 Customer？ | 保持聚合边界独立，避免跨聚合修改与一致性扩散 |
| 你们项目 DDD 怎么落地的？ | 核心域（订单）走聚合+仓储，外围域贫血；MyBatis 方案 PO/领域对象分离，仓储接口倒置 |
| 什么是防腐层？ | 消费者侧对上游模型的翻译/降级层，防止对方模型污染自己上下文 |

### 本章小结

- **战术设计定位**（41.1）：战略画地图、战术画施工图；领域模型最纯粹、基础设施依赖倒置；
- **战略基础**（41.2）：限界上下文（同一词不同义）、通用语言（术语直接进代码）；
- **实体**（41.3）：有标识、可变化、id 判等（状态变化不影响身份）；
- **值对象**（41.4）：无标识、不可变、值相等、行为内聚（Money 是教科书范例，Java 17 用 record）；
- **聚合**（41.5）：一致性边界、只经聚合根操作、单事务单聚合、跨聚合只存 id——本章核心；
- **领域服务与工厂**（41.6）：跨对象规则放领域服务，复杂创建用工厂；
- **仓储**（41.7）：接口定义在领域层、实现在基础设施层；MyBatis（PO 分离）与 JPA 两种落法；
- **实战**（41.8）：订单聚合四层完整落地 + 领域层毫秒级单测；
- **进阶模式**（41.9）：规格模式（组合判定）、防腐层（上下文间翻译隔离）；
- **坑与最佳实践**（41.10）：聚合过大、可变集合泄漏、Mapper 冒充仓储、伪 DDD 等 10 坑。

配套：第四十章（充血模型是战术设计前提）、第三十一章（商城实战改造对象）、第十七章 MyBatis-Plus（仓储实现）、第二十八章（依赖倒置）、第十章 MapStruct（Assembler 自动转换）。

---

至此，第 41 章 DDD 战术设计学习完成。下一章 [42-SpringStateMachine.md](./42-SpringStateMachine.md)（用状态机框架把聚合内的状态流转工程化）｜ 返回：[README.md](./README.md)
