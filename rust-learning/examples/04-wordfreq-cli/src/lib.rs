//! 词频统计核心库：与 CLI 入口分离，可独立单测、供他处复用。

use std::collections::HashMap;

/// 清洗并统计词频，返回按次数降序的 `(词, 次数)`；次数相同按字典序。
/// 忽略大小写与标点（`split` 按"非字母数字"切分，天然处理空白与标点）。
pub fn top_words(text: &str, limit: usize) -> Vec<(String, usize)> {
    let mut freq: HashMap<String, usize> = HashMap::new();
    for raw in text.split(|c: char| !c.is_alphanumeric()) {
        if raw.is_empty() {
            continue;
        }
        let word = raw.to_lowercase(); // 忽略大小写
        *freq.entry(word).or_insert(0) += 1;
    }

    let mut items: Vec<(String, usize)> = freq.into_iter().collect();
    // 次数降序；并列时按词字典序升序（输出稳定、可测试）
    items.sort_by(|a, b| b.1.cmp(&a.1).then_with(|| a.0.cmp(&b.0)));
    items.truncate(limit);
    items
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 忽略大小写且并列按字典序() {
        // go 与 rust 都出现 2 次 → 按字典序 go < rust
        let out = top_words("Go Rust GO rust Java", 2);
        assert_eq!(out, vec![("go".to_string(), 2), ("rust".to_string(), 2)]);
    }

    #[test]
    fn 标点与空白被忽略() {
        let out = top_words("a, b! a? c.   a", 10);
        assert_eq!(out, vec![("a".to_string(), 3), ("b".to_string(), 1), ("c".to_string(), 1)]);
    }

    #[test]
    fn 空文本() {
        assert!(top_words("", 10).is_empty());
    }
}
