//! HTTP 处理函数：参数校验 + 调用数据层 + 统一错误返回
use crate::db;
use crate::error::ApiError;
use crate::models::{Account, TransferReq};
use crate::AppState;
use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::Json;
use serde::Deserialize;
use std::sync::Arc;

#[derive(Deserialize)]
pub struct CreateAccountReq {
    name: String,
    balance: f64,
}

pub async fn list_accounts(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<Account>>, ApiError> {
    let accounts = db::list_all(&state.pool).await?;
    Ok(Json(accounts))
}

pub async fn create_account(
    State(state): State<Arc<AppState>>,
    Json(req): Json<CreateAccountReq>,
) -> Result<(StatusCode, Json<Account>), ApiError> {
    let name = req.name.trim();
    if name.is_empty() {
        return Err(ApiError::BadRequest("账户名称不能为空".to_string()));
    }
    if req.balance < 0.0 {
        return Err(ApiError::BadRequest("初始余额不能为负".to_string()));
    }
    let account = db::create(&state.pool, name, req.balance).await?;
    Ok((StatusCode::CREATED, Json(account)))
}

pub async fn get_account(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
) -> Result<Json<Account>, ApiError> {
    match db::get_one(&state.pool, id).await? {
        Some(account) => Ok(Json(account)),
        None => Err(ApiError::NotFound(format!("账户 {id} 不存在"))),
    }
}

pub async fn transfer(
    State(state): State<Arc<AppState>>,
    Path(from_id): Path<i64>,
    Json(req): Json<TransferReq>,
) -> Result<StatusCode, ApiError> {
    if from_id == req.to_id {
        return Err(ApiError::BadRequest("不能向自己转账".to_string()));
    }
    db::transfer(&state.pool, from_id, req.to_id, req.amount).await?;
    Ok(StatusCode::NO_CONTENT)
}
