//! 统一错误模型：所有处理函数返回 Result<_, ApiError>，自动映射为 HTTP 响应
use crate::db::TransferError;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde_json::json;

pub enum ApiError {
    NotFound(String),
    BadRequest(String),
    Internal(anyhow::Error),
}

// 数据访问错误自动汇入（? 即可传播 sqlx::Error）
impl From<sqlx::Error> for ApiError {
    fn from(e: sqlx::Error) -> Self {
        tracing::error!("数据库错误: {e:#}");
        ApiError::Internal(anyhow::anyhow!("数据库错误: {e}"))
    }
}

impl From<TransferError> for ApiError {
    fn from(e: TransferError) -> Self {
        match e {
            TransferError::NotFound(id) => ApiError::NotFound(format!("账户 {id} 不存在")),
            TransferError::InvalidAmount(amount) => {
                ApiError::BadRequest(format!("转账金额必须大于 0：{amount}"))
            }
            TransferError::Insufficient {
                from,
                balance,
                amount,
            } => ApiError::BadRequest(format!(
                "账户 {from} 余额不足：余额 {balance}，需转账 {amount}"
            )),
            TransferError::Db(e) => ApiError::from(e),
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let (status, message) = match self {
            ApiError::NotFound(msg) => (StatusCode::NOT_FOUND, msg),
            ApiError::BadRequest(msg) => (StatusCode::BAD_REQUEST, msg),
            ApiError::Internal(e) => {
                tracing::error!("内部错误: {e:#}");
                (StatusCode::INTERNAL_SERVER_ERROR, "服务器内部错误".to_string())
            }
        };
        (status, Json(json!({ "error": message }))).into_response()
    }
}
