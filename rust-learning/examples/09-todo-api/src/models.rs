//! 领域模型：任务状态机 + 传输对象。
//! 规则写在领域类型中（advance），非法状态在编译期/领域层被拒绝。
use serde::{Deserialize, Serialize};

/// 任务状态机：Todo -> InProgress -> Done
/// snake_case 序列化：todo / in_progress / done
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TaskStatus {
    Todo,
    InProgress,
    Done,
}

impl TaskStatus {
    /// 写入数据库的文本（可与 serde 命名不同）
    pub fn as_db_str(self) -> &'static str {
        match self {
            TaskStatus::Todo => "todo",
            TaskStatus::InProgress => "in_progress",
            TaskStatus::Done => "done",
        }
    }

    /// 从数据库文本解析；未知值按 Todo 兜底（本示例只写自己产生的值）
    pub fn from_db_str(s: &str) -> TaskStatus {
        match s {
            "in_progress" => TaskStatus::InProgress,
            "done" => TaskStatus::Done,
            _ => TaskStatus::Todo,
        }
    }

    /// 状态推进；非法迁移（Done 再推进）返回当前状态
    pub fn advance(self) -> Result<TaskStatus, TaskStatus> {
        match self {
            TaskStatus::Todo => Ok(TaskStatus::InProgress),
            TaskStatus::InProgress => Ok(TaskStatus::Done),
            TaskStatus::Done => Err(TaskStatus::Done),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Task {
    pub id: i64,
    pub title: String,
    pub status: TaskStatus,
}

#[derive(Debug, Deserialize)]
pub struct CreateTaskReq {
    pub title: String,
}

/// 查询参数：?done=false 表示只返回未完成；?done=true 只返回已完成
#[derive(Debug, Deserialize)]
pub struct ListQuery {
    pub done: Option<bool>,
}
