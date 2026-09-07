//! HTTP 处理函数
use crate::error::ApiError;
use crate::models::{CreateTaskReq, ListQuery, Task};
use crate::repository;
use crate::AppState;
use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::Json;
use std::sync::Arc;

pub async fn list_tasks(
    State(state): State<Arc<AppState>>,
    Query(query): Query<ListQuery>,
) -> Result<Json<Vec<Task>>, ApiError> {
    let tasks = repository::list(&state.pool, query.done).await?;
    Ok(Json(tasks))
}

pub async fn create_task(
    State(state): State<Arc<AppState>>,
    Json(req): Json<CreateTaskReq>,
) -> Result<(StatusCode, Json<Task>), ApiError> {
    let title = req.title.trim();
    if title.is_empty() {
        return Err(ApiError::BadRequest("任务标题不能为空".to_string()));
    }
    let task = repository::create(&state.pool, title).await?;
    Ok((StatusCode::CREATED, Json(task)))
}

pub async fn get_task(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
) -> Result<Json<Task>, ApiError> {
    match repository::get(&state.pool, id).await? {
        Some(task) => Ok(Json(task)),
        None => Err(ApiError::NotFound(format!("任务 {id} 不存在"))),
    }
}

pub async fn advance_task(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
) -> Result<Json<Task>, ApiError> {
    let task = repository::advance(&state.pool, id).await?;
    Ok(Json(task))
}
