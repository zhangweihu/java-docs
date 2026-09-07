//! 数据访问层：SQLx + SQLite，领域模型与行记录双向映射
use crate::models::{Task, TaskStatus};
use sqlx::sqlite::SqliteRow;
use sqlx::{Row, SqlitePool};

pub async fn init_schema(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'todo'
        )",
    )
    .execute(pool)
    .await?;
    Ok(())
}

fn row_to_task(row: &SqliteRow) -> Result<Task, sqlx::Error> {
    let status: String = row.try_get("status")?;
    Ok(Task {
        id: row.try_get("id")?,
        title: row.try_get("title")?,
        status: TaskStatus::from_db_str(&status),
    })
}

pub async fn create(pool: &SqlitePool, title: &str) -> Result<Task, sqlx::Error> {
    let row = sqlx::query(
        "INSERT INTO tasks (title, status) VALUES (?, 'todo') RETURNING id, title, status",
    )
    .bind(title)
    .fetch_one(pool)
    .await?;
    row_to_task(&row)
}

pub async fn get(pool: &SqlitePool, id: i64) -> Result<Option<Task>, sqlx::Error> {
    let row = sqlx::query("SELECT id, title, status FROM tasks WHERE id = ?")
        .bind(id)
        .fetch_optional(pool)
        .await?;
    row.as_ref().map(row_to_task).transpose()
}

pub async fn list(pool: &SqlitePool, done: Option<bool>) -> Result<Vec<Task>, sqlx::Error> {
    let mut sql = String::from("SELECT id, title, status FROM tasks");
    match done {
        Some(true) => sql.push_str(" WHERE status = 'done'"),
        Some(false) => sql.push_str(" WHERE status != 'done'"),
        None => {}
    }
    sql.push_str(" ORDER BY id");

    let rows = sqlx::query(&sql).fetch_all(pool).await?;
    rows.iter().map(row_to_task).collect()
}

/// advance 专用错误：区分 任务不存在 / 非法状态迁移 / 存储错误
pub enum AdvanceError {
    NotFound(i64),
    IllegalTransition { id: i64, from: TaskStatus },
    Db(sqlx::Error),
}

impl From<sqlx::Error> for AdvanceError {
    fn from(e: sqlx::Error) -> Self {
        AdvanceError::Db(e)
    }
}

/// 领域规则在 advance() 中执行；本函数是"读 -> 推进 -> 写回"的服务编排
pub async fn advance(pool: &SqlitePool, id: i64) -> Result<Task, AdvanceError> {
    let current = get(pool, id)
        .await?
        .ok_or(AdvanceError::NotFound(id))?;

    let next = current
        .status
        .advance()
        .map_err(|from| AdvanceError::IllegalTransition { id, from })?;

    sqlx::query("UPDATE tasks SET status = ? WHERE id = ?")
        .bind(next.as_db_str())
        .bind(id)
        .execute(pool)
        .await?;

    get(pool, id).await?.ok_or(AdvanceError::NotFound(id))
}
