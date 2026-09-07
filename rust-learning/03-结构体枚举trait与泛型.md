# 第三章：结构体、枚举、trait 与泛型

> 目标：用 `struct`/`enum`/`match`/`trait`/泛型描述领域模型，掌握 `Option`/`Result`、`impl` 方法与 trait 默认实现/派生、`dyn Trait` 动态分发，完成一个**待办事项领域模型**。
>
> 前置：[02-所有权借用与生命周期.md](./02-所有权借用与生命周期.md) ｜ 下一章：[04-集合迭代器闭包模块与错误处理.md](./04-集合迭代器闭包模块与错误处理.md)

## 3.1 struct：定义自己的类型

```rust
struct Todo {
    id: u64,
    title: String,
    done: bool,
}

fn main() {
    // 实例化：必须初始化全部字段
    let mut item = Todo {
        id: 1,
        title: String::from("学习 Rust"),
        done: false,
    };
    item.done = true;            // 修改字段需要结构体本身可变
    println!("{}: {}", item.id, item.title);
}
```

**元组结构体**（字段无名）与**单元结构体**（无字段，可作类型标记）：

```rust
struct Score(i32);               // 元组结构体
struct App;                      // 单元结构体，常与 trait 配合作"标记"
```

结构体支持**更新语法**（复制其余字段，触发 move/Copy 语义）与**解构**：

```rust
struct Point { x: f64, y: f64 }

let origin = Point { x: 0.0, y: 0.0 };
let p = Point { x: 1.0, ..origin };          // 其余字段取 origin（x 也 Copy 时整体可用）
let Point { x, y } = p;                       // 解构绑定
```

## 3.2 impl：方法

方法第一个参数是 `self`（或其引用），用 `impl` 块定义；`Self` 表示"本类型"：

```rust
impl Todo {
    /// 关联函数（类似 Java 静态方法/构造器），没有 self
    fn new(id: u64, title: &str) -> Self {
        Todo { id, title: title.to_string(), done: false }
    }

    fn toggle(&mut self) {
        self.done = !self.done;
    }

    fn is_pending(&self) -> bool {
        !self.done
    }
}

fn main() {
    let mut item = Todo::new(1, "学习 Rust");   // 关联函数用 :: 调用
    item.toggle();                              // 方法用 . 调用
    assert!(item.done);
}
```

## 3.3 enum：让非法状态不可表示

Rust 的枚举是**代数数据类型（ADT）**，可携带数据，比 Java 的 enum 更强大：

```rust
enum TaskStatus {
    Todo,                                // 无数据
    InProgress { started_at: String },   // 结构体变体
    Done(u32),                           // 元组变体：完成于第几天
}
```

把"当前状态可能携带什么数据"写进类型，**编译期就消灭非法状态**。例如：只有 `Done` 才有完成日期，其他状态根本构造不出来。

`match` 必须穷尽所有变体，缺失分支会编译失败：

```rust
fn describe(status: &TaskStatus) -> String {
    match status {
        TaskStatus::Todo => "未开始".to_string(),
        TaskStatus::InProgress { started_at } => format!("进行中（{started_at}）"),
        TaskStatus::Done(day) => format!("第 {day} 天完成"),
    }
}
```

`if let` 用于只关心一种变体、其他忽略：

```rust
if let TaskStatus::Done(day) = status {
    println!("完成了 {day}");
}
```

## 3.4 Option 与 Result：没有 null

Rust 没有 `null`。可能"没有值"用 `Option<T>`，可能"失败"用 `Result<T, E>`，二者都是标准库枚举：

```rust
enum Option<T> { None, Some(T) }
enum Result<T, E> { Ok(T), Err(E) }
```

**要点**：编译器强制你处理"没有值/失败"的情况，无法像空指针一样穿透业务代码。读取用 `match`、`unwrap_or`、`?` 等，写业务逻辑时优先用显式处理而不是 `unwrap()`：

```rust
fn parse_number(s: &str) -> Result<i32, String> {
    s.parse::<i32>().map_err(|_| format!("不是数字: {s}"))
}

fn main() {
    let value = parse_number("42").unwrap_or(-1);        // 失败给默认值
    if let Ok(n) = parse_number("42") {
        println!("{n}");
    }
}
```

`?` 运算符：函数返回 `Result` 时，错误自动向上传播（`Err` 提前返回）；普通函数中 `?` 会把 `Err` 通过 `from` 转换后直接返回（要求函数签名匹配）：

```rust
fn add_ten(input: &str) -> Result<i32, String> {
    let n: i32 = input.parse().map_err(|_| "解析失败".to_string())?;
    Ok(n + 10)
}
```

## 3.5 trait：共享行为的契约

`trait` 定义一组行为（类似 Java interface）。**为类型实现 trait**：

```rust
trait Summarizable {
    fn summarize(&self) -> String;
    // 可以有默认实现，类型可实现覆盖
    fn tagline(&self) -> String {
        format!("<{}>", self.summarize())
    }
}

struct Task { title: String }

impl Summarizable for Task {
    fn summarize(&self) -> String {
        format!("任务: {}", self.title)
    }
}
```

**trait 对象 `dyn Trait`**（动态分发）：运行时决定具体实现，`&dyn Trait`/`Box<dyn Trait>` 允许不同类型放进同一集合。代价是**失去静态分发时的内联优化**并引入虚表查找，但换来多态灵活性：

```rust
// 静态分发：编译期单态化，每个具体类型生成一份代码（性能最好）
fn render_static<T: Summarizable>(item: &T) -> String { item.summarize() }

// 动态分发：运行时查虚表
fn render_dyn(item: &dyn Summarizable) -> String { item.summarize() }
```

何时用哪个？**集合需要装不同类型 / 运行时才知道类型 / 插件化** → `dyn`；其余尽量静态分发或泛型。常见场景如 `Box<dyn Error>`、`Box<dyn Handler>`。

**Trait Bound（约束）**：声明泛型必须实现某个 trait：

```rust
fn longest<'a, T: PartialOrd>(left: &'a T, right: &'a T) -> &'a T {
    if left >= right { left } else { right }
}
```

多约束与 `where` 写法（更清晰）：

```rust
fn merge<T>(a: T, b: T) -> T
where
    T: std::ops::Add<Output = T>,
{
    a + b
}
```

**Derive（派生宏）**：为常见 trait 一键生成实现：

```rust
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
struct Todo {
    id: u64,
    title: String,
}
```

`Debug` 让结构体可直接 `{:?}` 打印；`Clone` 深拷贝；`PartialEq` 比较相等；`Eq` 全等价；`Hash` 作 HashMap key。

## 3.6 实战：待办事项领域模型

**需求**：用 DDD 风格建模一个待办系统核心：`Task` 聚合、状态机（Todo → InProgress → Done）、排序与查询谓词，用 `trait` 抽象存储。

```rust
#[derive(Debug, Clone, PartialEq)]
enum Status {
    Todo,
    InProgress,
    Done,
}

impl Status {
    /// 状态推进：Todo → InProgress → Done，其余返回错误
    fn advance(&self) -> Result<Status, String> {
        match self {
            Status::Todo => Ok(Status::InProgress),
            Status::InProgress => Ok(Status::Done),
            Status::Done => Err("已完成的任务不能再推进".to_string()),
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
struct Task {
    id: u64,
    title: String,
    status: Status,
    priority: u8, // 1 最高
}

impl Task {
    fn new(id: u64, title: &str) -> Self {
        Task { id, title: title.to_string(), status: Status::Todo, priority: 3 }
    }

    fn advance(&mut self) -> Result<(), String> {
        self.status = self.status.advance()?;
        Ok(())
    }

    fn with_priority(mut self, priority: u8) -> Self {
        self.priority = priority;
        self
    }
}

// 用 trait 抽象"任务存储"，方便测试用内存实现、生产用数据库实现
trait TaskRepository {
    fn save(&mut self, task: Task) -> Result<(), String>;
    fn find(&self, id: u64) -> Option<&Task>;
    fn pending(&self) -> Vec<&Task>;   // 未完成的任务，按优先级排序
}

struct InMemoryRepo {
    tasks: Vec<Task>,
}

impl TaskRepository for InMemoryRepo {
    fn save(&mut self, task: Task) -> Result<(), String> {
        if let Some(existing) = self.tasks.iter_mut().find(|t| t.id == task.id) {
            *existing = task;                      // 更新
        } else {
            self.tasks.push(task);                 // 新增
        }
        Ok(())
    }

    fn find(&self, id: u64) -> Option<&Task> {
        self.tasks.iter().find(|t| t.id == id)
    }

    fn pending(&self) -> Vec<&Task> {
        let mut items: Vec<&Task> = self.tasks.iter().filter(|t| t.status != Status::Done).collect();
        items.sort_by_key(|t| t.priority);
        items
    }
}

fn main() -> Result<(), String> {
    let mut repo = InMemoryRepo { tasks: Vec::new() };
    repo.save(Task::new(1, "学习 struct").with_priority(1))?;
    repo.save(Task::new(2, "学习 enum"))?;

    let mut task1 = repo.find(1).unwrap().clone(); // 读出来再改（避免借用冲突）
    task1.advance()?;                               // Todo → InProgress
    repo.save(task1)?;

    println!("待办（按优先级）:");
    for task in repo.pending() {
        println!("  [{}] {} -> {:?}", task.priority, task.title, task.status);
    }
    Ok(())
}
```

**验收清单**：

1. `cargo run` 输出两条待办且按优先级排序；
2. 对已 `Done` 的任务调用 `advance()` 返回 `Err`，`main` 用 `?` 传播后可打印错误；
3. 给 `Task` 增加 `rename(&mut self, new_title: &str)` 方法并调用；
4. 新增枚举 `enum PriorityFilter { All, Only(u8) }`，用 `match` 实现过滤逻辑。

**扩展练习（建模能力训练）**：为"订单系统"设计 `enum OrderState { Created, Paid{paid_at}, Shipped{tracking_no}, Delivered, Cancelled{reason} }`，列出哪些状态迁移是合法的，并用 `advance` 风格实现（提示：比 Todo 多两维判断）。

> 关键收获：Rust 用类型把"约束"表达在编译期——`enum` 消灭非法状态、`Option/Result` 消灭空指针、`trait` 提供多态而不引入继承的复杂性。这是 Rust 面向对象思想的核心：**组合与行为契约优先于继承**。下一章进入标准库大杀器：集合、迭代器、闭包与错误处理工程化。

上一章：[02-所有权借用与生命周期.md](./02-所有权借用与生命周期.md) ｜ 下一章：[04-集合迭代器闭包模块与错误处理.md](./04-集合迭代器闭包模块与错误处理.md) ｜ [返回 README](./README.md)
