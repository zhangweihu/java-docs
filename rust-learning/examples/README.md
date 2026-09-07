# Rust examples —— 配套可运行工程

> 与 [Rust 学习指南](../README.md) 的 **01~09 章** 一一对应的示例工程。每个目录都是一个独立的 Cargo 工程，进入目录执行 `cargo run` 即可。

## 工程一览

| 目录 | 对应章 | 一句话 | 网络/DB 依赖 |
| --- | --- | --- | --- |
| [01-calculator](./01-calculator) | 第一章 | 命令行计算器（`+ - * /`，含错误处理与单元测试） | 无 |
| [02-word-stats](./02-word-stats) | 第二章 | 文本统计（字符/单词/行数/高频词，支持读文件） | 无 |
| [03-todo-model](./03-todo-model) | 第三章 | 待办领域模型（状态机 + 仓储 trait + 内存实现） | 无 |
| [04-wordfreq-cli](./04-wordfreq-cli) | 第四章 | 词频统计 CLI（lib/main 分离 + anyhow 错误上下文） | 无 |
| [05-expression-cache](./05-expression-cache) | 第五章 | 表达式树求值（Box）+ Rc/RefCell 共享缓存 | 无 |
| [06-concurrent-scheduler](./06-concurrent-scheduler) | 第六章 | 并发任务调度（线程 + mpsc + 锁进度） | 无 |
| [07-async-fetcher](./07-async-fetcher) | 第七章 | 异步并发抓取（默认本地模拟，`--real` 走 reqwest） | `--real` 需网络 |
| [08-accounting-api](./08-accounting-api) | 第八章 | 记账 REST 服务（Axum + SQLx + SQLite + 转账事务） | SQLite 本地文件 |
| [09-todo-api](./09-todo-api) | 第九章 | 待办 REST 服务（领域状态机 + HTTP 级测试，可交付形态） | SQLite 本地文件 |

## 通用命令

```bash
cd <某个工程目录>
cargo run            # 运行
cargo test           # 运行测试（08/09 内含接口级测试）
cargo check          # 快速检查
cargo clippy -- -D warnings
cargo fmt --check
```

## 建议使用顺序

```text
01 -> 02 -> 03 -> 04 -> 05 -> 06 -> 07 -> 08 -> 09
```

- 前 7 个工程无任何外部服务依赖，`cargo run` 即可看到输出；
- `08-accounting-api` / `09-todo-api` 使用 SQLite 文件持久化（无需安装数据库），运行后会自动在目录下生成 `.db` 文件；用 curl/浏览器访问 `http://localhost:8080`；
- 各工程 `Cargo.toml` 顶部注释标明了它演示的核心概念，建议对照对应章节文档阅读源码；
- 版本提示：示例依赖（axum/sqlx/polars/tokio 等）声明为兼容的大版本，请以 `cargo build` 实际解析到的版本为准；若个别 crate API 有演进，文档/代码注释已给出应对提示。
