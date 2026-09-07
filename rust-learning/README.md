# Rust 学习指南（从基础到工程实践）

> 本文档与 [java-learning](../java-learning/README.md)、[python-learning](../python-learning/README.md) 并列，面向**已有 Java/Python 或其他编程基础的开发者**。目标是用一条清晰路线掌握 Rust 的核心语法、内存安全模型、并发与异步开发，并完成可部署的 Web 服务。
>
> Rust 的学习重点不是记住更多语法，而是理解**所有权、借用、生命周期和 trait** 如何共同保证内存安全与可维护性。建议每个章节都亲手编写、编译并运行示例，完成每章末尾的**实战与验收**。
>
> ✅ **13 章体系化学习文档 + 每章实战 + `examples/` 配套可运行工程已全部发布**：
> 主线 9 章（基础语法 → 所有权 → 类型系统 → 集合/错误 → 智能指针 → 并发 → 异步 → Web → 工程化）
> ＋ 高级专题 4 章（过程宏 → unsafe/FFI → Polars 数据分析 → 大数据与爬虫实战），一条龙学完。

## 目录结构

| 章节 | 内容 | 文件 | 状态 |
| --- | --- | --- | --- |
| 第一章 | Rust 环境与基础语法：rustup/Cargo/rustfmt/clippy、变量与可变性、标量与复合类型、函数与表达式、流程控制、字符串与切片入门 | [01-Rust环境与基础语法.md](./01-Rust环境与基础语法.md) | ✅ 已发布 |
| 第二章 | 所有权、借用与生命周期：移动与 Copy、借用规则、切片、生命周期标注与省略、常见借用错误与修复 | [02-所有权借用与生命周期.md](./02-所有权借用与生命周期.md) | ✅ 已发布 |
| 第三章 | 结构体、枚举、trait 与泛型：struct/impl、enum 与 match、Option/Result、方法、trait 默认实现与派生、泛型与 trait bound、dyn Trait 动态分发 | [03-结构体枚举trait与泛型.md](./03-结构体枚举trait与泛型.md) | ✅ 已发布 |
| 第四章 | 集合、迭代器、闭包、模块与错误处理：Vec/String/HashMap/HashSet、迭代器组合子、闭包捕获、模块与可见性、Cargo workspace、thiserror/anyhow | [04-集合迭代器闭包模块与错误处理.md](./04-集合迭代器闭包模块与错误处理.md) | ✅ 已发布 |
| 第五章 | 智能指针与内存管理：Box/Rc/Arc/RefCell/Mutex/RwLock/Weak、内部可变性、引用循环与内存泄漏、Drop 语义 | [05-智能指针与内存管理.md](./05-智能指针与内存管理.md) | ✅ 已发布 |
| 第六章 | 并发编程：线程、通道 mpsc、Send/Sync、Arc/Mutex 共享状态、屏障与 scoped 线程、并发模型选型 | [06-并发编程.md](./06-并发编程.md) | ✅ 已发布 |
| 第七章 | 异步 Rust 与 Tokio：Future/async-await、Tokio 运行时、Join/Select、超时与取消、spawn_blocking 与背压 | [07-异步Rust与Tokio.md](./07-异步Rust与Tokio.md) | ✅ 已发布 |
| 第八章 | Axum Web 开发与数据库：路由/State/提取器/响应、Serde 序列化、统一错误处理、SQLx 连接池与 migration、tracing 日志、部署 | [08-Axum-Web与数据库.md](./08-Axum-Web与数据库.md) | ✅ 已发布 |
| 第九章 | 测试、质量与发布：单元/集成/属性测试、Cargo fmt/clippy、可观测性、Docker 打包、CI、交叉编译、体系总结与主线收官 | [09-测试质量与发布.md](./09-测试质量与发布.md) | ✅ 已发布 |
| 第十章 | **高级专题**·过程宏与元编程：macro_rules! 声明宏、过程宏三兄弟、syn/quote 自定义派生宏完整工程、属性宏与函数式宏、cargo expand 调试 | [10-过程宏与元编程.md](./10-过程宏与元编程.md) | ✅ 已发布 |
| 第十一章 | **高级专题**·unsafe 与 FFI：unsafe 五大能力、UB 与 Miri、安全封装模式、extern "C"/repr(C)/CString、导出给 C 与 cbindgen、bindgen/wasm、环形缓冲实战 | [11-unsafe与FFI.md](./11-unsafe与FFI.md) | ✅ 已发布 |
| 第十二章 | **高级专题**·Polars 数据分析：Arrow 列式模型、DataFrame 表达式 API、惰性框架与查询优化、Parquet IO、缺失值、订单数据分析实战 | [12-Polars数据分析.md](./12-Polars数据分析.md) | ✅ 已发布 |
| 第十三章 | **高级专题**·大数据与爬虫实战：reqwest/scraper 爬虫、并发限速与优雅抓取、Parquet 落地、DataFusion SQL 查询引擎、采集→清洗→分析全链路、与 java 大数据体系对照 | [13-大数据与爬虫实战.md](./13-大数据与爬虫实战.md) | ✅ 已发布 |

## examples/ 配套可运行工程

每章实战均配有可 `cargo run` 的完整示例工程（见 [examples/README.md](./examples/README.md)）：

| 工程 | 对应章 | 说明 |
| --- | --- | --- |
| `examples/01-calculator` | 第一章 | 命令行计算器（含单元测试） |
| `examples/02-word-stats` | 第二章 | 文本统计工具（字符/单词/行数/高频词） |
| `examples/03-todo-model` | 第三章 | 待办领域模型 + 仓储 trait 抽象 |
| `examples/04-wordfreq-cli` | 第四章 | 单词频率统计 CLI（库/入口分离 + anyhow） |
| `examples/05-expression-cache` | 第五章 | 表达式树 + Rc/RefCell 共享缓存 |
| `examples/06-concurrent-scheduler` | 第六章 | 并发任务调度器（线程 + mpsc + 锁进度） |
| `examples/07-async-fetcher` | 第七章 | 并发 URL 抓取器（Tokio + 超时） |
| `examples/08-accounting-api` | 第八章 | 记账 REST 服务（Axum + SQLx + SQLite） |
| `examples/09-todo-api` | 第九章 | 待办 REST 服务（领域状态机 + HTTP 测试，可交付版） |

## 学习路线

```text
环境搭建
   |
基础语法与类型
   |
所有权/借用/生命周期
   |
结构体/枚举/trait/泛型
   |
集合/错误处理/模块与 Cargo
   |
智能指针与内存管理
   |
并发与消息传递
   |
异步 Rust（Tokio）
   |
Web 开发（Axum）与数据库
   |
测试/性能/安全/发布（主线 9 章收官）
   |
过程宏与元编程（高级）
   |
unsafe 与 FFI（高级）
   |
Polars 数据分析（高级）
   |
大数据与爬虫实战（高级收官）
```

主线：**语法 → 类型系统 → 内存 → 并发 → 异步 → Web → 工程化**（1~9 章），之后是**高级专题**（10~13 章：元编程 / unsafe-FFI / Polars / 大数据爬虫）。每一章都以"实战项目"收尾，建议按顺序推进：

| 阶段 | 核心内容 | 章节实战产出 |
| --- | --- | --- |
| 第一阶段 | rustup/Cargo、变量、类型、函数、流程控制 | 命令行计算器 |
| 第二阶段 | 所有权、借用、切片、生命周期 | 文本统计工具 |
| 第三阶段 | struct、enum、match、Option/Result、trait、泛型 | 待办事项领域模型 |
| 第四阶段 | Vec/String/HashMap、迭代器、闭包、模块、workspace、错误处理 | 单词频率统计 CLI |
| 第五阶段 | Box/Rc/Arc/RefCell/Mutex 等智能指针 | 表达式树与共享缓存 |
| 第六阶段 | 线程、mpsc、Send/Sync、共享状态 | 并发任务调度器 |
| 第七阶段 | async/await、Tokio、超时、取消 | 并发 URL 抓取器 |
| 第八阶段 | Axum 路由/状态/提取器、SQLx、Serde | RESTful 记账服务 |
| 第九阶段 | 单元/集成/属性测试、Clippy、Docker、CI | 可发布的待办 REST 服务 |
| 第十阶段 | macro_rules!/过程宏（syn/quote 派生宏） | 自定义 `#[derive(Describe)]` 宏工程 |
| 第十一阶段 | unsafe 五大能力、FFI/extern "C"/cbindgen | 安全的环形缓冲 + C 调用示例 |
| 第十二阶段 | Polars DataFrame/惰性框架/Parquet | 订单数据分析（CSV→Parquet） |
| 第十三阶段 | 爬虫 reqwest/scraper + DataFusion SQL | 采集→清洗→SQL 分析全链路管道 |

## 与 Java/Python 的概念对照

| Rust | Java | Python |
| --- | --- | --- |
| `struct` / `impl` | class | class / dataclass |
| `trait` | interface | protocol / duck typing |
| `enum` | enum + sealed 类型组合 | Enum / 联合类型 |
| `Option<T>` | `Optional<T>` | `None` |
| `Result<T, E>` | 异常或返回对象 | 异常或返回值 |
| 所有权与借用 | GC + 引用 | 引用计数/GC + 引用 |
| `Cargo` | Maven/Gradle | uv/poetry/pip |
| Tokio | CompletableFuture / Reactor | asyncio |
| Axum | Spring WebMVC / WebFlux | FastAPI |
| SQLx | MyBatis / JPA | SQLAlchemy |
| tracing | SLF4J + Logback | logging |
| `#[tokio::main]` | `@SpringBootApplication` | `asyncio.run()` |

## 环境要求

- Rust 工具链（rustup 安装 stable）：https://rustup.rs/
- 推荐组件：`rustfmt`、`clippy`，开发期可用 `rust-analyzer` 插件（VS Code / IntelliJ Rust）
- 数据库章节需要 SQLite（无需安装服务端）或 Docker 拉取 PostgreSQL/MySQL
- Web 章节可直接用 `cargo run` 本地验证，测试 `tower-http` 中间件链

## 学习建议

1. **不要跳过所有权和借用**：先用编译器错误理解规则，再记 API；编译器是 Rust 最好的老师；
2. 每个示例都运行 `cargo fmt`、`cargo clippy`、`cargo test`；
3. 先用标准库完成小工具，再引入第三方 crate；
4. **必须独立完成每章实战**：只有亲手写过，所有权和生命周期才会内化；
5. 学会阅读 crate 文档（docs.rs）、源码与编译器诊断，Rust 的工具链本身就是学习的一部分。

## 实战项目进阶路线

```
命令行计算器（01）→ 文本统计（02）→ 领域模型（03）→ 词频 CLI（04）
      → 共享状态实验（05）→ 并发调度器（06）→ 异步抓取器（07）
      → 记账 REST 服务（08）→ 可发布待办服务 + CI/Docker（09 主线收官）
      → 派生宏工程（10）→ 环形缓冲/FFI（11）→ 数据分析（12）
      → 爬虫+DataFusion 管道（13 高级收官）
```

每个工程的代码位于 [`examples/`](./examples/README.md)，对应关系见上文表格。

下一步：从 [01-Rust环境与基础语法.md](./01-Rust环境与基础语法.md) 开始，先安装 Rust 并跑通第一个 Cargo 项目；每章学完到 `examples/` 运行配套工程验证。
