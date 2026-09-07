//! 统一错误模型：业务错误映射明确状态码，未知错误记日志返回 500
use crate::repository::AdvanceError;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde_json::json;

pub enum ApiError {
    NotFound(String),
    BadRequest(String),
    Conflict(String),
    Internal(anyhow::Error),
}

impl From<sqlx::Error> for ApiError {
    fn from(e: sqlx::Error) -> Self {
        tracing::error!("数据库错误: {e:#}");
        ApiError::Internal(anyhow::anyhow!("数据库错误: {e}"))
    }
}

impl From<AdvanceError> for ApiError {
    fn from(e: AdvanceError) -> Self {
        match e {
            AdvanceError::NotFound(id) => ApiError::NotFound(format!("任务 {id} 不存在")),
            AdvanceError::IllegalTransition { id, from } => {
                let from_str = match from {
                    crate::models::TaskStatus::Todo => "todo",
                    crate::models::TaskStatus::InProgress => "in_progress",
                    crate::models::TaskStatus::Done => "done",
                };
                ApiError::Conflict(format!("任务 {id} 当前状态 {from_str}，无法继续推进"))
            }
            AdvanceError::Db(e) => ApiError::from(e),
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let (status, message) = match self {
            ApiError::NotFound(msg) => (StatusCode::NOT_FOUND, msg),
            ApiError::BadRequest(msg) => (StatusCode::BAD_REQUEST, msg),
            ApiError::Conflict(msg) => (StatusCode::CONFLICT, msg),
            ApiError::Internal(e) => {
                tracing::error!("内部错误: {e:#}");
                (StatusCode::INTERNAL_SERVER_ERROR, "服务器内部错误".to_string())
            }
        };
        (status, Json(json!({ "error": message }))).into_response()
    }
}
