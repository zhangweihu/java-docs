# 第七章：异步 Rust 与 Tokio

> 目标：理解 `Future`/`async`/`await` 模型、Tokio 运行时、并发组合子（`join!`/`select!`）、超时与取消、CPU 任务卸载（`spawn_blocking`）与背压，完成一个**并发 URL 抓取器**。
>
> 前置：[06-并发编程.md](./06-并发编程.md) ｜ 下一章：[08-Axum-Web与数据库.md](./08-Axum-Web与数据库.md)

## 7.1 为什么需要异步

Web 服务与客户端大量时间在**等待 I/O**（网络往返、磁盘读取）。一个请求在等待时，线程若是阻塞的，就只能靠"开更多线程"扛并发，代价高。异步编程让单个线程在 I/O 等待期间**切去执行别的任务**，从而以少量线程支撑海量并发连接。

- 系统线程：由 OS 调度，创建与切换开销大，每线程栈占用大；
- 异步任务：由运行时（Tokio）在少量 OS 线程上调度，Task 轻量（KB 级栈、由堆分配），可支撑数十万并发。

**选型铁律**：I/O 密集（网络/文件/数据库）→ 异步；CPU 密集长任务 → 线程/rayon（或 `spawn_blocking` 丢到线程池）。

## 7.2 async / await 与 Future

```rust
// main 需要异步运行时：最简方式 #[tokio::main]
async fn fetch_title(url: &str) -> Result<String, reqwest::Error> {
    let body = reqwest::get(url).await?.text().await?;
    // 伪实现：真实解析 HTML 需要 crate，这里返回前 50 字符
    Ok(body.chars().take(50).collect())
}

#[tokio::main]
async fn main() -> Result<(), reqwest::Error> {
    let title = fetch_title("https://example.com").await?;
    println!("{title}");
    Ok(())
}
```

`async fn` 返回一个 `Future`。`Future` 是惰性的：**不 poll 就不会执行**，`.await` 才是"轮询它直到就绪"。所以异步代码天然是"并发编排"语言：

- 一个 `.await` 挂起点表示"让出，等数据就绪"；
- 运行时负责在挂起时调度其他任务、数据就绪后继续推进。

`reqwest`（HTTP 客户端）与 `tokio` 一起使用是最常见的起步组合。

## 7.3 Tokio 运行时

运行时 = 线程池（默认与 CPU 核数相等）+ 任务调度器 + I/O 事件驱动（epoll/io_uring 等）。

```rust
use tokio::runtime::Builder;

fn main() {
    let rt = Builder::new_multi_thread()
        .worker_threads(4)
        .enable_all()
        .build()
        .unwrap();

    rt.block_on(async {
        println!("运行在 Tokio 运行时中");
        // async_main().await;
    });
}
```

绝大多数应用直接：

```rust
#[tokio::main]                  // 展开为一个多线程运行时 + block_on
async fn main() {}
```

Tokio 的 `spawn` 在运行时内产生独立任务，任务并发执行：

```rust
#[tokio::main]
async fn main() {
    let task = tokio::spawn(async {
        21 * 2
    });
    let result = task.await.unwrap();   // JoinHandle: await 拿结果，unwrap 处理 panic
    println!("{result}");
}
```

## 7.4 并发组合：join! 与 select!

- **`join!`**：同时等待多个 future 全部完成（等价"并行等待"）：
  ```rust
  let (a, b) = tokio::join!(fetch("https://a.com"), fetch("https://b.com"));
  ```
- **`tokio::spawn` + await**：各自独立运行，适合每个分支耗时不同/持续任务的场景；
- **`select!`**：等待多个 future，**最先就绪的那个**继续，其余被取消：

```rust
use tokio::time::{sleep, timeout, Duration};

#[tokio::main]
async fn main() {
    tokio::select! {
        v = compute() => println!("compute 先完成: {v}"),
        _ = sleep(Duration::from_secs(1)) => println!("1 秒超时"),
    }
}

async fn compute() -> i32 {
    sleep(Duration::from_millis(500)).await;
    42
}
```

## 7.5 超时与取消

- `timeout(duration, future)`：超过时限返回 `Err(Elapsed)`，底层 future 被丢弃（取消）；
- `CancellationToken`（`tokio_util`）可协作式取消长时间任务；
- 组合子：`select!` 的分支未选中即被取消。

```rust
use tokio::time::{timeout, Duration};

async fn slow_call() -> &'static str {
    tokio::time::sleep(Duration::from_secs(3)).await;
    "ok"
}

#[tokio::main]
async fn main() {
    match timeout(Duration::from_secs(1), slow_call()).await {
        Ok(v) => println!("成功: {v}"),
        Err(_) => println!("调用超时，已取消"),
    }
}
```

## 7.6 spawn_blocking：不要阻塞异步线程

异步线程不应执行阻塞操作（同步数据库驱动、CPU 密集计算），否则会阻塞整个线程池的调度。把它们交回阻塞线程池：

```rust
#[tokio::main]
async fn main() {
    // 同步、CPU 密集或阻塞 I/O 的工作
    let heavy = tokio::task::spawn_blocking(|| {
        // 这里用普通同步代码，在独立线程池执行
        let mut total = 0u64;
        for i in 0..10_000_000u64 { total += i; }
        total
    })
    .await
    .unwrap();
    println!("{heavy}");
}
```

判断标准：方法**是否返回 Future**。`reqwest`/`sqlx` 异步驱动返回 Future → 直接 await；`std::fs`/`std::net`/第三方同步库 → 包进 `spawn_blocking`。

## 7.7 背压与有界通道

生产速度超过消费速度时，无界缓冲会吞掉内存。Tokio 的 `mpsc` 提供**有界通道**——发送端满了会 `await` 等待（或 `try_send` 失败），天然形成背压：

```rust
use tokio::sync::mpsc;

#[tokio::main]
async fn main() {
    let (tx, mut rx) = mpsc::channel::<u64>(10);      // 容量 10

    let producer = tokio::spawn(async move {
        for i in 0..100u64 {
            tx.send(i).await.unwrap();     // 满了会等待，形成背压
        }
    });

    let consumer = tokio::spawn(async move {
        while let Some(v) = rx.recv().await {
            println!("消费 {v}");
        }
    });

    let _ = tokio::join!(producer, consumer);
}
```

通道用尽即优雅结束（`recv` 返回 `None`），是流式任务的标准退出机制。

## 7.8 实战：并发 URL 抓取器

**需求**：给定一组 URL，并发抓取状态码与内容长度，全部结束后统一输出；单个 URL 超时 5 秒不拖垮整体；统计总耗时。

```rust
use std::time::Instant;
use tokio::time::{timeout, Duration};

async fn probe(url: &str) -> (String, Result<usize, String>) {
    let result = timeout(Duration::from_secs(5), reqwest::get(url)).await;
    match result {
        Ok(Ok(resp)) => {
            let status = resp.status().as_u16();
            let body = resp.text().await.unwrap_or_default();
            let outcome = if status == 200 {
                Ok(body.len())
            } else {
                Err(format!("HTTP {status}"))
            };
            (url.to_string(), outcome)
        }
        Ok(Err(e)) => (url.to_string(), Err(format!("请求失败: {e}"))),
        Err(_) => (url.to_string(), Err("超时(>5s)".to_string())),
    }
}

#[tokio::main]
async fn main() {
    let urls = vec![
        "https://www.rust-lang.org",
        "https://example.com",
        "https://httpbin.org/status/404",
        "https://www.rust-lang.org/learn",
    ];

    let started = Instant::now();
    // 用 futures::future::join_all 并发等待全部（等量 URL 也可用 tokio::task::JoinSet）
    let results = futures::future::join_all(urls.iter().map(|u| probe(u))).await;

    for (url, outcome) in results {
        match outcome {
            Ok(len) => println!("{url}: 成功，内容 {len} 字节"),
            Err(reason) => println!("{url}: {reason}"),
        }
    }
    println!("总耗时: {:?}", started.elapsed());
}
```

**运行前提**：`cargo add tokio --features full`、`cargo add reqwest --features json`、`cargo add futures`。

**验收清单**：

1. 程序输出每个 URL 的结果与总耗时（全部并发，总耗时 ≈ 最慢单个，而非求和）；
2. 故意放一个"黑洞" URL（如不存在的本地端口），验证超时分支生效；
3. 把 `.map(|u| probe(u))` 改成逐个 `.await`（串行），对比总耗时差异，并解释；
4. 用 `JoinSet` 重构：逐个 `spawn` + 边完成边打印。

**扩展练习**：增加 `--limit`（最大并发数）用信号量 `tokio::sync::Semaphore` 限流；把每个 URL 的内容保存为文件并打印文件名；解析 `<title>` 提取标题。

> 关键收获：异步不是"更快"，而是"同样的资源服务更多并发"。写代码时先分清哪些操作会等（I/O），再决定直接 await、join! 并行还是 spawn 后台。下一章把这些技能串成一个真实服务：Axum Web 开发与数据库。

上一章：[06-并发编程.md](./06-并发编程.md) ｜ 下一章：[08-Axum-Web与数据库.md](./08-Axum-Web与数据库.md) ｜ [返回 README](./README.md)
