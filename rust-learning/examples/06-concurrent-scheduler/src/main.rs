//! 第六章实战：并发任务调度器
//!
//! 场景：并发执行 N 个模拟任务，维护共享进度计数（Arc<Mutex>），
//! 结果经 mpsc 通道回收，主线程等待全部完成并汇总。
use std::sync::mpsc;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

/// 模拟一个耗时任务：返回 (任务序号, 产出)
fn do_work(id: usize) -> (usize, u64) {
    thread::sleep(Duration::from_millis((id as u64 + 1) * 50));
    (id, (id as u64 + 1) * 10)
}

/// 执行 [start, end) 号任务（含并发核心逻辑；返回按序号排序的结果）
fn run_jobs(total: usize) -> Vec<(usize, u64)> {
    let started = Instant::now();
    let progress = Arc::new(Mutex::new(0usize));
    let (tx, rx) = mpsc::channel();

    let mut workers = vec![];
    for id in 0..total {
        let progress = Arc::clone(&progress);
        let tx = tx.clone();
        workers.push(thread::spawn(move || {
            let (task_id, output) = do_work(id);
            {
                let mut p = progress.lock().unwrap();
                *p += 1;
                println!("任务 {task_id} 完成（进度 {}/{}）", *p, total);
            }
            tx.send((task_id, output)).unwrap();
        }));
    }
    // 关闭主线程的发送端副本，rx.iter() 才能在全部 worker 结束后返回 None
    drop(tx);

    let mut results: Vec<(usize, u64)> = rx.iter().collect();
    results.sort_by_key(|&(task_id, _)| task_id);

    println!("总进度: {}", *progress.lock().unwrap());
    println!("并发执行总耗时: {:?}", started.elapsed());
    results
}

fn main() {
    const TASKS: usize = 6;
    let results = run_jobs(TASKS);

    let total_output: u64 = results.iter().map(|&(_, output)| output).sum();
    for (task_id, output) in &results {
        println!("  => 任务 {task_id} 产出 {output}");
    }
    println!("总产出: {total_output}");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 串行参考值() {
        // 串行执行 3 个任务，核对 run_jobs 的结果集一致
        let mut expected: Vec<(usize, u64)> = (0..3).map(do_work).collect();
        expected.sort_by_key(|&(task_id, _)| task_id);
        assert_eq!(run_jobs(3), expected);
    }
}
