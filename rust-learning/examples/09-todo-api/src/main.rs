//! 第九章实战：待办 REST 服务（可运行 + HTTP 级测试）
//!
//! 接口：
//!   POST   /tasks                 {"title":"学习 Rust"}
//!   GET    /tasks?done=false      列表（可按完成态过滤）
//!   GET    /tasks/{id}
//!   POST   /tasks/{id}/advance    状态推进 Todo -> InProgress -> Done（非法推进返回 409）
//!
//! 环境变量：DATABASE_URL（默认 sqlite:todo.db）、PORT（默认 8080）
//! 运行：cargo run；测试：cargo test（使用 sqlite::memory:）
mod error;
mod handlers;
mod models;
mod repository;

use axum::routing::{get, post};
use axum::Router;
use sqlx::SqlitePool;
use std::sync::Arc;
use tokio::net::TcpListener;

/// with_state 注入的共享依赖（连接池）
#[derive(Clone)]
pub struct AppState {
    pub pool: SqlitePool,
}

/// 组装应用：main 与测试共用同一构建函数
fn build_app(pool: SqlitePool) -> Router {
    Router::new()
        .route("/ping", get(|| async { "pong" }))
        .route("/tasks", get(handlers::list_tasks).post(handlers::create_task))
        .route("/tasks/{id}", get(handlers::get_task))
        .route("/tasks/{id}/advance", post(handlers::advance_task))
        .with_state(Arc::new(AppState { pool }))
        .layer(tower_http::trace::TraceLayer::new_for_http())
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .init();

    let database_url =
        std::env::var("DATABASE_URL").unwrap_or_else(|_| "sqlite:todo.db".to_string());
    let pool = SqlitePool::connect(&database_url).await?;
    repository::init_schema(&pool).await?;

    let port = std::env::var("PORT").unwrap_or_else(|_| "8080".to_string());
    let listener = TcpListener::bind(format!("0.0.0.0:{port}")).await?;
    tracing::info!("listening on http://{}", listener.local_addr()?);

    axum::serve(listener, build_app(pool)).await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use http_body_util::BodyExt;
    use sqlx::sqlite::SqlitePoolOptions;
    use tower::ServiceExt;

    /// 每个测试独立的内存数据库（max_connections=1 保证同一连接，
    /// sqlite::memory: 才能跨语句共享表结构）
    async fn test_pool() -> SqlitePool {
        let pool = SqlitePoolOptions::new()
            .max_connections(1)
            .connect("sqlite::memory:")
            .await
            .unwrap();
        repository::init_schema(&pool).await.unwrap();
        pool
    }

    async fn body_text(response: axum::response::Response) -> String {
        let bytes = response.into_body().collect().await.unwrap().to_bytes();
        String::from_utf8(bytes.to_vec()).unwrap()
    }

    fn post_json(uri: &str, json: &str) -> Request<Body> {
        Request::builder()
            .method("POST")
            .uri(uri)
            .header("content-type", "application/json")
            .body(Body::from(json.to_string()))
            .unwrap()
    }

    #[tokio::test]
    async fn 创建与推进状态() {
        let app = build_app(test_pool().await);

        let resp = app.clone().oneshot(post_json("/tasks", r#"{"title":"学习 Rust"}"#)).await.unwrap();
        assert_eq!(resp.status(), StatusCode::CREATED);
        let created: serde_json::Value =
            serde_json::from_str(&body_text(resp).await).unwrap();
        assert_eq!(created["status"], "todo");
        let id = created["id"].as_i64().unwrap();

        // 推进两次到 done
        let resp = app.clone().oneshot(Request::builder().method("POST").uri(format!("/tasks/{id}/advance")).body(Body::empty()).unwrap()).await.unwrap();
        assert_eq!(resp.status(), StatusCode::OK);
        let resp = app.clone().oneshot(Request::builder().method("POST").uri(format!("/tasks/{id}/advance")).body(Body::empty()).unwrap()).await.unwrap();
        assert_eq!(resp.status(), StatusCode::OK);

        // Done 后再推进 -> 409（领域规则生效）
        let resp = app.clone().oneshot(Request::builder().method("POST").uri(format!("/tasks/{id}/advance")).body(Body::empty()).unwrap()).await.unwrap();
        assert_eq!(resp.status(), StatusCode::CONFLICT);
        let body = body_text(resp).await;
        assert!(body.contains("无法继续推进"), "错误信息应可读: {body}");
    }

    #[tokio::test]
    async fn 列表过滤与404() {
        let app = build_app(test_pool().await);
        app.clone()
            .oneshot(post_json("/tasks", r#"{"title":"任务A"}"#))
            .await
            .unwrap();
        app.clone()
            .oneshot(post_json("/tasks", r#"{"title":"任务B"}"#))
            .await
            .unwrap();

        // 全部
        let resp = app.clone().oneshot(Request::builder().uri("/tasks").body(Body::empty()).unwrap()).await.unwrap();
        let all: serde_json::Value = serde_json::from_str(&body_text(resp).await).unwrap();
        assert_eq!(all.as_array().unwrap().len(), 2);

        // 未完成过滤
        let resp = app.clone().oneshot(Request::builder().uri("/tasks?done=false").body(Body::empty()).unwrap()).await.unwrap();
        let pending: serde_json::Value = serde_json::from_str(&body_text(resp).await).unwrap();
        assert_eq!(pending.as_array().unwrap().len(), 2);

        // 不存在的任务 -> 404
        let resp = app.clone().oneshot(Request::builder().uri("/tasks/999").body(Body::empty()).unwrap()).await.unwrap();
        assert_eq!(resp.status(), StatusCode::NOT_FOUND);

        // 空标题 -> 400
        let resp = app.oneshot(post_json("/tasks", r#"{"title":"   "}"#)).await.unwrap();
        assert_eq!(resp.status(), StatusCode::BAD_REQUEST);
    }
}
