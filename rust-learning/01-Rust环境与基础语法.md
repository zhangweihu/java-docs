# 第一章：Rust 环境与基础语法

> 目标：装好工具链、跑通第一个 Cargo 项目、掌握变量/类型/函数/流程控制/表达式，完成一个**命令行计算器**。
>
> 前置：[返回 README](./README.md) ｜ 下一章：[02-所有权借用与生命周期.md](./02-所有权借用与生命周期.md)

## 1.1 安装 Rust

Windows 推荐使用 `rustup` 安装。安装完成后重新打开终端：

```powershell
rustup default stable
rustc --version
cargo --version
```

推荐同时安装组件：

```powershell
rustup component add rustfmt
rustup component add clippy
```

工具职责：

- `rustup`：管理工具链、目标平台和组件；
- `rustc`：Rust 编译器；
- `cargo`：创建项目、管理依赖、编译、测试和发布；
- `rustfmt`：统一代码格式；
- `clippy`：静态检查与惯用写法建议。

IDE 插件：VS Code 安装 `rust-analyzer`（官方推荐），IntelliJ 系使用 `IntelliJ Rust`。

## 1.2 创建第一个项目

```powershell
cargo new hello-rust
cd hello-rust
cargo run
cargo test
cargo fmt --check
cargo clippy --all-targets --all-features -- -D warnings
```

Cargo 项目结构：

```text
hello-rust/
├── Cargo.toml       # 项目元数据与依赖声明
├── Cargo.lock       # 锁定依赖精确版本（应用建议提交）
└── src/
    └── main.rs
```

常用命令速查：

```powershell
cargo check                 # 只检查，不生成可执行文件，速度快（日常最常用）
cargo build                 # Debug 构建
cargo build --release       # Release 构建（启用优化）
cargo run -- arg1           # 运行并传递命令行参数
cargo test                  # 执行测试
cargo doc --open            # 生成并打开 API 文档
cargo tree                  # 查看依赖树
```

> **工作习惯**：先用 `cargo check` 快速反馈，再用 `cargo test` 验证行为；`cargo fmt` 和 `cargo clippy` 每次提交前固定执行。

第一个程序（`src/main.rs`）：

```rust
fn main() {
    let language = "Rust";
    println!("正在学习 {language}");
}
```

`println!` 是宏（末尾 `!`），支持 `{}` 占位与 `{name}` 命名捕获。`Cargo.toml` 中 `[package]` 描述当前包，`[dependencies]` 描述第三方依赖。

## 1.3 变量与可变性

Rust 变量**默认不可变**——这不是限制，而是帮助编译器和读代码的人判断状态是否会改变。要修改必须先显式声明 `mut`：

```rust
fn main() {
    let name = "Rust";          // 不可变绑定
    let mut count = 0;          // 可变绑定

    count += 1;                 // 编译通过（count 声明了 mut）
    // name = "Java";           // 编译失败：name 不可变

    println!("{name}: {count}");
}
```

**Shadowing（变量遮蔽）**允许用同名新变量覆盖旧值，常用于"变换类型/临时取值"而不需要 `mut`：

```rust
fn main() {
    let value = "42";
    let value: u32 = value.parse().expect("不是数字"); // 类型由 &str 变为 u32
    let value = value * 2;                            // 值再次变换
    println!("{value}");
}
```

`const` 与 `let` 的区别：`const` 必须在编译期确定为常量、类型必须显式标注、且总是不可变；`let` 是运行时绑定。

## 1.4 基本类型

常见标量类型：整数、浮点数、布尔、字符；复合类型：元组、数组。

```rust
let integer: i32 = 42;
let large = integer as i64;              // 显式类型转换用 as
let pair: (i32, &str) = (42, "rust");    // 元组
let numbers: [i32; 3] = [1, 2, 3];       // 数组（长度固定）
```

Rust **不会默认进行可能造成精度或范围问题的隐式类型转换**，整数默认推导为 `i32`，浮点默认 `f64`。

整数类型 `i8/i16/i32/i64/i128` 与对应无符号 `u8..u128`、`isize/usize`（与指针同宽，常用于下标）。涉及金额、计数与下标时**应明确选择类型**，避免溢出：

```rust
let big: u64 = 10_000_000_000;       // 数字字面量可用 _ 分隔
let byte: u8 = 0xFF;                  // 十六进制
let bin: u8 = 0b1010;                 // 二进制
let ok: bool = true;                  // 布尔
let ch: char = 'R';                   // 字符（Unicode 标量值）
```

> Debug 构建下整数溢出会 panic，Release 构建下回绕（默认关闭检查）。需要确定性行为可显式使用 `wrapping_add`/`saturating_add`/`checked_add`。

## 1.5 函数与表达式

函数用 `fn` 声明，参数必须标注类型。**代码块最后一个表达式作为返回值**——末尾不写分号；带分号的是语句，不会返回该值：

```rust
fn classify(score: u32) -> &'static str {
    if score >= 90 {
        "excellent"          // 没有分号 → 作为表达式返回
    } else if score >= 60 {
        "pass"
    } else {
        "retry"
    }
}

fn add(a: i32, b: i32) -> i32 {
    a + b                    // 等价于 return a + b;
}

fn main() {
    for score in [95, 72, 48] {
        println!("{}", classify(score));
    }
    println!("{}", add(2, 3));
}
```

`if`、`match`、代码块都是**表达式**，可以赋值给变量：

```rust
fn discount_level(total: f64) -> &'static str {
    // if 作为表达式
    let level = if total >= 1_000.0 { "gold" } else { "normal" };
    level
}
```

**发散函数**（`-> !`，如 `panic!`、`todo!`、`unimplemented!`）可充当任何类型：

```rust
fn not_implemented() -> i32 {
    todo!("稍后实现")
}
```

## 1.6 流程控制

- `if / else if / else`：条件表达式，无隐式真值转换（必须是 `bool`）；
- `loop`：无限循环，可用 `break value` 返回值、`continue` 跳过；
- `while`：条件循环；
- `for`：最常用，遍历迭代器（`0..5` 是 Range，`..=` 是闭区间）。

```rust
fn main() {
    let mut n = 0;
    let result = loop {
        n += 1;
        if n == 10 {
            break n * 2;     // loop 可以作为表达式返回 20
        }
    };
    println!("loop result = {result}");

    for i in 1..=3 {         // 闭区间，1 2 3
        if i == 2 { continue; }
        print!("{i} ");
    }

    let mut count = 3;
    while count > 0 {
        count -= 1;
    }
}
```

## 1.7 字符串与切片入门（详述见第二章）

`String` 是可增长、**拥有所有权**的 UTF-8 字符串；`&str` 是字符串切片，通常只**借用**一段字符串。由于 UTF-8 一个字符可能占多个字节，不能随意按整数下标取字符：

```rust
let text = String::from("你好 Rust");
println!("字节数: {}", text.len());          // 9（"你好"各占 3 字节 + 空格 + 4 字节英文）
println!("字符数: {}", text.chars().count()); // 6
```

函数参数优先使用 `&str`，这样既能接收字符串字面量，也能接收 `String` 的借用：

```rust
fn greeting(name: &str) -> String {
    format!("Hello, {name}")
}

fn main() {
    let who = String::from("Rust");
    println!("{}", greeting(&who));
    println!("{}", greeting("world"));       // &str 直接传入
}
```

## 1.8 实战：命令行计算器

**需求**：从命令行接收两个数字与一个运算符（`+ - * /`），计算并输出结果；除数为零返回可读错误而不是崩溃；运算符非法时给出提示。

**第 1 步：只做加法**，跑通 I/O 流程（学习 `std::env::args` 读取参数、`parse` 解析、`?` 暂不引入）：

```rust
use std::env;

fn main() {
    let args: Vec<String> = env::args().collect();
    // args[0] 是程序名，参数从 1 开始
    if args.len() != 4 {
        println!("用法: calc <a> <运算符> <b>，例如: calc 3 + 4");
        return;
    }

    let a: f64 = match args[1].parse() {
        Ok(value) => value,
        Err(_) => {
            println!("第一个参数不是有效数字: {}", args[1]);
            return;
        }
    };
    let op = &args[2];
    let b: f64 = match args[3].parse() {
        Ok(value) => value,
        Err(_) => {
            println!("第三个参数不是有效数字: {}", args[3]);
            return;
        }
    };

    let result = match op.as_str() {
        "+" => a + b,
        "-" => a - b,
        "*" => a * b,
        "/" => {
            if b == 0.0 {
                println!("错误: 除数不能为 0");
                return;
            }
            a / b
        }
        _ => {
            println!("不支持的运算符: {op}（支持 + - * /）");
            return;
        }
    };
    println!("{a} {op} {b} = {result}");
}
```

**第 2 步：重构为可测试的纯函数**（把计算逻辑与 I/O 分离，方便后续章节补单元测试）：

```rust
#[derive(Debug, PartialEq)]
enum CalcError {
    InvalidNumber(String),
    DivideByZero,
    UnknownOperator(String),
}

fn calculate(a: f64, op: &str, b: f64) -> Result<f64, CalcError> {
    match op {
        "+" => Ok(a + b),
        "-" => Ok(a - b),
        "*" => Ok(a * b),
        "/" => {
            if b == 0.0 {
                Err(CalcError::DivideByZero)
            } else {
                Ok(a / b)
            }
        }
        _ => Err(CalcError::UnknownOperator(op.to_string())),
    }
}

fn main() {
    // ...读取参数同上...
    let result = calculate(a, op, b);
    match result {
        Ok(value) => println!("{a} {op} {b} = {value}"),
        Err(e) => println!("计算失败: {e:?}"),
    }
}
```

**验收清单**：

1. `cargo new calculator` 后把上述代码写入 `src/main.rs`；
2. `cargo run -- 3 + 4` 输出 `7`；
3. `cargo run -- 5 / 0` 输出可读错误且**不 panic**；
4. `cargo run -- 3`（参数不足）与 `cargo run -- 1 ^ 2`（非法运算符）都有提示；
5. `cargo fmt --check`、`cargo clippy -- -D warnings` 零告警。

**扩展练习**（任选）：支持 `%`（取模）；支持 `sqrt` 等函数名；用 `loop + 标准输入读取`（`std::io::stdin`）实现交互式计算器。

> 关键收获：Rust 没有 `null` 引发的崩溃式异常，`Result` 迫使你在边界处理失败；把"可能失败"写进类型，是 Rust 工程化的第一课。下一章将理解这一切背后的机制——所有权与借用。

上一章：无（本系列第一章） ｜ 下一章：[02-所有权借用与生命周期.md](./02-所有权借用与生命周期.md) ｜ [返回 README](./README.md)
