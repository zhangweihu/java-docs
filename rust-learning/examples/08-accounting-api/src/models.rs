//! 数据模型：账户与转账请求
use serde::{Deserialize, Serialize};
use sqlx::FromRow;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct Account {
    pub id: i64,
    pub name: String,
    /// 金额（单位：元）。演示用 f64；生产金额建议 i64 分或 NUMERIC，避免精度问题
    pub balance: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransferReq {
    pub to_id: i64,
    pub amount: f64,
}
