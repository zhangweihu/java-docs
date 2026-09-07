//! 第七章实战：异步并发抓取
//!
//! 默认模式（无需网络）：本地演示并发任务与超时取消；
//!   cargo run
//! 真实模式（需联网）：并发抓取 URL 并输出状态码/内容长度
//!   cargo run -- --real
use std::time::{Duration, Instant};
use tokio::time::sleep;

/// 模拟一个"等待 I/O"的任务（在真实场景中是网络/磁盘请求）
async fn simulate_io(id: usize) -> usize {
    sleep(Duration::from_millis((id as u64) * 60)).await;
    id * id
}

/// 演示 A：并发发起 6 个模拟任务，全部完成后汇总
async fn demo_concurrent() {
    let started = Instant::now();

    let mut handles = Vec::new();
    for id in 1..=6 {
        handles.push(tokio::spawn(simulate_io(id)));
    }

    let mut sum = 0;
    for handle in handles {
        sum += handle.await.expect("任务 panic");
    }
    // 并发执行，总耗时约等于最慢任务（360ms），而不是串行求和
    println!("并发 6 任务平方和 = {sum}，耗时 {:.2}s", started.elapsed().as_secs_f64());
}

/// 演示 B：超时与取消（select!/timeout）
async fn slow_call() -> &'static str {
    sleep(Duration::from_secs(3)).await;
    "ok"
}

async fn demo_timeout() {
    match tokio::time::timeout(Duration::from_millis(300), slow_call()).await {
        Ok(value) => println!("成功: {value}"),
        Err(_) => println!("调用超时，任务已被取消 ✓"),
    }
}

// ---------- 真实抓取（需网络，遵守目标站点规则） ----------

async fn probe(client: &reqwest::Client, url: &str) -> (String, Result<usize, String>) {
    let result = tokio::time::timeout(Duration::from_secs(5), client.get(url).send()).await;
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

async fn real_fetch() -> anyhow::Result<()> {
    let client = reqwest::Client::builder()
        .user_agent("async-fetcher-demo/0.1 (+learning)")
        .build()?;

    let urls = vec![
        "https://www.rust-lang.org/",
        "https://example.com/",
        "https://www.rust-lang.org/learn",
    ];

    let started = Instant::now();
    let results = futures::future::join_all(urls.iter().map(|u| probe(&client, u))).await;

    for (url, outcome) in results {
        match outcome {
            Ok(len) => println!("{url}: 成功，内容 {len} 字节"),
            Err(reason) => println!("{url}: {reason}"),
        }
    }
    println!("总耗时: {:.2}s", started.elapsed().as_secs_f64());
    Ok(())
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    if std::env::args().any(|a| a == "--real") {
        real_fetch().await?;
    } else {
        demo_concurrent().await;
        demo_timeout().await;
    }
    Ok(())
}
