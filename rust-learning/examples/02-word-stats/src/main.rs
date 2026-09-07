//! 第二章实战：文本统计工具
//!
//! 运行：
//!   cargo run -- "hello rust 你好"     # 直接统计一段文本
//!   cargo run -- -f xxx.txt            # 统计文件
use std::collections::HashMap;
use std::process::exit;

/// 统计结果：字符数、单词数、行数、最高频词(词, 次数)
#[derive(Debug)]
struct Stats {
    chars: usize,
    words: usize,
    lines: usize,
    top_word: Option<(String, usize)>,
}

/// 统计文本。key 用 String（让 map 拥有数据），避免循环内局部 String 的借用悬垂
fn analyze(text: &str) -> Stats {
    let mut freq: HashMap<String, usize> = HashMap::new();
    let mut word_count = 0;

    for raw in text.split_whitespace() {
        // 去标点：只保留字母数字（含中文）
        let clean: String = raw.chars().filter(|c| c.is_alphanumeric()).collect();
        if !clean.is_empty() {
            word_count += 1;
            *freq.entry(clean).or_insert(0) += 1;
        }
    }

    let top_word = freq.into_iter().max_by_key(|&(_, n)| n);
    Stats {
        chars: text.chars().count(),
        words: word_count,
        lines: text.lines().count(),
        top_word,
    }
}

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();

    // 支持读文件：word-stats -f path
    let text = if args.len() == 2 && args[0] == "-f" {
        match std::fs::read_to_string(&args[1]) {
            Ok(content) => content,
            Err(e) => {
                eprintln!("读取文件 {} 失败: {e}", args[1]);
                exit(1);
            }
        }
    } else {
        args.join(" ")
    };

    if text.trim().is_empty() {
        eprintln!("请输入要统计的文本，或用 -f 指定文件");
        exit(1);
    }

    let stats = analyze(&text);
    println!("字符数: {}", stats.chars);
    println!("单词数: {}", stats.words);
    println!("行数:   {}", stats.lines);
    match &stats.top_word {
        Some((word, n)) => println!("最高频词: {word}（{n} 次）"),
        None => println!("最高频词: 无"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 中英文混合统计() {
        // hello 唯一最高频（3 次），避免并列时 HashMap 遍历顺序不确定
        let s = analyze("hello hello rust 你好\nhello");
        assert_eq!(s.words, 4);
        assert_eq!(s.lines, 2);
        assert_eq!(s.top_word.as_ref().map(|(w, n)| (w.as_str(), *n)), Some(("hello", 3)));
    }

    #[test]
    fn 标点不计入单词() {
        let s = analyze("a, b! c?  ");
        assert_eq!(s.words, 3);
    }
}
