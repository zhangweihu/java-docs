//! 第四章实战：词频统计 CLI 入口
//!
//! 运行：
//!   cargo run -- notes.txt          # 默认输出 Top10
//!   cargo run -- notes.txt 5        # 输出 Top5
//!   cargo run -- not-exist.txt      # 可读的错误信息
use anyhow::Context;
use wordfreq_cli::top_words;

fn main() -> anyhow::Result<()> {
    let mut args = std::env::args().skip(1);
    let path = args
        .next()
        .context("用法: wordfreq-cli <文件> [TopN]，例如 wordfreq-cli notes.txt 10")?;
    let limit: usize = match args.next() {
        Some(raw) => raw.parse().context("TopN 必须是正整数")?,
        None => 10,
    };

    let content = std::fs::read_to_string(&path)
        .with_context(|| format!("无法读取文件 {path}"))?;

    for (word, count) in top_words(&content, limit) {
        println!("{word:>14}: {count}");
    }
    Ok(())
}
