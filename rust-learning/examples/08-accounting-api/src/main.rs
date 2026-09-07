//! 第八章实战：记账 REST 服务（可运行示例）
//!
//! 接口：
//!   POST   /accounts                {"name":"奶茶","balance":100}
//!   GET    /accounts
//!   GET    /accounts/{id}
//!   POST   /accounts/{id}/transfer  {"to_id":2,"amount":30}   （事务转账）
//!
//! 环境变量：DATABASE_URL（默认 sqlite:accounting.db）、PORT（默认 8080）
//! 启动后访问 http://localhost:8080
mod db;
mod error;
mod handlers;
mod models;

use axum::routing::get;
use axum::Router;
use sqlx::SqlitePool;
use std::sync::Arc;
use tokio::net::TcpListener;

/// 通过 with_state 注入各 handler 的共享依赖
#[derive(Clone)]
pub struct AppState {
    pub pool: SqlitePool,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .init();

    let database_url =
        std::env::var("DATABASE_URL").unwrap_or_else(|_| "sqlite:accounting.db".to_string());
    let pool = db::init_db(&database_url).await?;
    let state = Arc::new(AppState { pool });

    let app = Router::new()
        .route("/ping", get(|| async { "pong" }))
        .route(
            "/accounts",
            get(handlers::list_accounts).post(handlers::create_account),
        )
        .route("/accounts/{id}", get(handlers::get_account))
        .route(
            "/accounts/{id}/transfer",
            axum::routing::post(handlers::transfer),
        )
        .with_state(state)
        .layer(tower_http::trace::TraceLayer::new_for_http());

    let port = std::env::var("PORT").unwrap_or_else(|_| "8080".to_string());
    let listener = TcpListener::bind(format!("0.0.0.0:{port}")).await?;
    tracing::info!("listening on http://{}", listener.local_addr()?);
    axum::serve(listener, app).await?;
    Ok(())
}
