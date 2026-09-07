//! 第三章实战：待办事项领域模型（DDD 风格）
//!
//! 演示：enum 表达状态机、trait 抽象存储（InMemory 实现）、借用与克隆的取舍
use std::collections::HashMap;

/// 任务状态机：Todo -> InProgress -> Done，非法迁移由类型+方法拒绝
#[derive(Debug, Clone, PartialEq)]
enum Status {
    Todo,
    InProgress,
    Done,
}

impl Status {
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

    fn with_priority(mut self, priority: u8) -> Self {
        self.priority = priority;
        self
    }

    fn advance(&mut self) -> Result<(), String> {
        self.status = self.status.advance()?;
        Ok(())
    }
}

/// 存储抽象：测试/内存用 InMemory，生产可用数据库实现
trait TaskRepository {
    fn save(&mut self, task: Task) -> Result<(), String>;
    fn find(&self, id: u64) -> Option<&Task>;
    fn pending(&self) -> Vec<&Task>;
}

struct InMemoryRepo {
    tasks: HashMap<u64, Task>,
}

impl TaskRepository for InMemoryRepo {
    fn save(&mut self, task: Task) -> Result<(), String> {
        if let Some(existing) = self.tasks.get_mut(&task.id) {
            *existing = task; // 更新
        } else {
            self.tasks.insert(task.id, task); // 新增
        }
        Ok(())
    }

    fn find(&self, id: u64) -> Option<&Task> {
        self.tasks.get(&id)
    }

    fn pending(&self) -> Vec<&Task> {
        let mut items: Vec<&Task> = self
            .tasks
            .values()
            .filter(|t| t.status != Status::Done)
            .collect();
        items.sort_by_key(|t| t.priority);
        items
    }
}

fn main() -> Result<(), String> {
    let mut repo = InMemoryRepo { tasks: HashMap::new() };
    repo.save(Task::new(1, "学习 struct").with_priority(1))?;
    repo.save(Task::new(2, "学习 enum"))?;

    // 读出来再改（避免借用冲突：不能持有 &Task 的同时修改 repo）
    let mut task1 = repo.find(1).unwrap().clone();
    task1.advance()?; // Todo -> InProgress
    repo.save(task1)?;

    println!("待办（按优先级）:");
    for task in repo.pending() {
        println!("  [P{}] {} -> {:?}", task.priority, task.title, task.status);
    }

    // 非法状态迁移演示：对已 Done 的任务 advance 应返回 Err
    let mut task2 = repo.find(2).unwrap().clone();
    task2.advance()?; // Todo -> InProgress
    task2.advance()?; // InProgress -> Done
    assert!(task2.advance().is_err(), "Done 状态不应能继续推进");
    println!("Done 后 advance 被正确拒绝 ✓");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 状态机合法迁移() {
        let mut t = Task::new(1, "demo");
        assert_eq!(t.status, Status::Todo);
        t.advance().unwrap();
        assert_eq!(t.status, Status::InProgress);
        t.advance().unwrap();
        assert_eq!(t.status, Status::Done);
        assert!(t.advance().is_err());
    }

    #[test]
    fn 仓储按优先级返回待办() {
        let mut repo = InMemoryRepo { tasks: HashMap::new() };
        repo.save(Task::new(1, "low")).unwrap();
        repo.save(Task::new(2, "high").with_priority(1)).unwrap();
        repo.save(Task::new(3, "done")).unwrap();
        // 构造一个 Done 任务需要可变借用：先取出克隆再改
        let mut done = repo.find(3).unwrap().clone();
        done.advance().unwrap();
        done.advance().unwrap();
        repo.save(done).unwrap();

        let pending = repo.pending();
        assert_eq!(pending.len(), 2);
        assert_eq!(pending[0].title, "high", "高优先级排最前");
    }
}
