//! 数据访问层：连接池初始化、账户 CRUD、转账事务（SQLite）
use crate::error::ApiError;
use crate::models::Account;
use sqlx::SqlitePool;

pub async fn init_db(database_url: &str) -> Result<SqlitePool, ApiError> {
    let pool = SqlitePool::connect(database_url).await?;
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS accounts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            balance REAL NOT NULL DEFAULT 0
        )",
    )
    .execute(&pool)
    .await?;
    Ok(pool)
}

pub async fn list_all(pool: &SqlitePool) -> Result<Vec<Account>, ApiError> {
    Ok(sqlx::query_as::<_, Account>("SELECT id, name, balance FROM accounts ORDER BY id")
        .fetch_all(pool)
        .await?)
}

pub async fn get_one(pool: &SqlitePool, id: i64) -> Result<Option<Account>, ApiError> {
    Ok(
        sqlx::query_as::<_, Account>("SELECT id, name, balance FROM accounts WHERE id = ?")
            .bind(id)
            .fetch_optional(pool)
            .await?,
    )
}

pub async fn create(pool: &SqlitePool, name: &str, balance: f64) -> Result<Account, ApiError> {
    Ok(sqlx::query_as::<_, Account>(
        "INSERT INTO accounts (name, balance) VALUES (?, ?) RETURNING id, name, balance",
    )
    .bind(name)
    .bind(balance)
    .fetch_one(pool)
    .await?)
}

/// 转账相关错误（业务规则错误先于 SQL 错误）
pub enum TransferError {
    NotFound(i64),
    InvalidAmount(f64),
    Insufficient { from: i64, balance: f64, amount: f64 },
    Db(sqlx::Error),
}

impl From<sqlx::Error> for TransferError {
    fn from(e: sqlx::Error) -> Self {
        TransferError::Db(e)
    }
}

/// 转账：同一事务内扣款 + 加款，任何一步失败整体回滚
pub async fn transfer(
    pool: &SqlitePool,
    from_id: i64,
    to_id: i64,
    amount: f64,
) -> Result<(), TransferError> {
    if amount <= 0.0 {
        return Err(TransferError::InvalidAmount(amount));
    }

    let mut tx = pool.begin().await?;

    let from: Account = sqlx::query_as::<_, Account>(
        "SELECT id, name, balance FROM accounts WHERE id = ?",
    )
    .bind(from_id)
    .fetch_optional(&mut *tx)
    .await?
    .ok_or(TransferError::NotFound(from_id))?;

    // 收款账户必须存在
    sqlx::query_as::<_, Account>("SELECT id, name, balance FROM accounts WHERE id = ?")
        .bind(to_id)
        .fetch_optional(&mut *tx)
        .await?
        .ok_or(TransferError::NotFound(to_id))?;

    if from.balance < amount {
        return Err(TransferError::Insufficient {
            from: from_id,
            balance: from.balance,
            amount,
        });
    }

    sqlx::query("UPDATE accounts SET balance = balance - ? WHERE id = ?")
        .bind(amount)
        .bind(from_id)
        .execute(&mut *tx)
        .await?;
    sqlx::query("UPDATE accounts SET balance = balance + ? WHERE id = ?")
        .bind(amount)
        .bind(to_id)
        .execute(&mut *tx)
        .await?;

    tx.commit().await?;
    Ok(())
}
