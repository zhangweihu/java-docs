# 第八章：Axum Web 开发与数据库

> 目标：掌握 Axum（路由/状态/提取器/统一错误/中间件）、Serde 序列化、SQLx 连接池与迁移、tracing 日志、JSON API 的完整写法，完成一个**记账 REST 服务**并跑通测试。
>
> 前置：[07-异步Rust与Tokio.md](./07-异步Rust与Tokio.md) ｜ 下一章：[09-测试质量与发布.md](./09-测试质量与发布.md)

## 8.1 技术栈与工程结构

Axum 是 Tokio 官方团队维护的 Web 框架，类型安全、基于 `tower`（中间件生态）。记账服务最小依赖：

```toml
[dependencies]
tokio = { version = "1", features = ["full"] }
axum = "0.7"
serde = { version = "1", features = ["derive"] }
serde_json = "1"
sqlx = { version = "0.8", features = ["runtime-tokio", "sqlite", "macros", "migrate"] }
tracing = "0.1"
tracing-subscriber = "0.3"
anyhow = "1"
```

工程结构建议（把 main.rs 里的"路由+处理函数"分层，后续扩展更顺）：

```text
accounting-server/
├── Cargo.toml
├── migrations/
│   └── 0001_create_accounts.sql
└── src/
    ├── main.rs        # 组装：数据库、状态、路由、监听
    ├── db.rs          # 连接池初始化、SQL 执行
    ├── models.rs      # 领域结构体（Account/Transaction）
    ├── handlers.rs    # HTTP 处理函数
    └── error.rs       # 统一错误类型 → 响应映射
```

## 8.2 核心概念速成

### Router：路由 + 处理函数 + 状态

```rust
use axum::{Router, routing::get, extract::State};
use std::sync::Arc;
use sqlx::SqlitePool;

async fn ping() -> &'static str {
    "pong"
}

fn build_app(pool: SqlitePool) -> Router {
    Router::new()
        .route("/ping", get(ping))
        .route("/accounts", axum::routing::get(list_accounts).post(create_account))
        .route("/accounts/{id}", axum::routing::get(get_account))
        .with_state(Arc::new(AppState { pool }))     // 用 State 共享数据库连接池
}

#[derive(Clone)]
struct AppState {
    pool: SqlitePool,
}
```

- `get(handler).post(handler)`：同一路径不同方法；
- `with_state`：把全局依赖（连接池、配置）注入处理函数；
- `Handler` 接收任何实现 `FromRequest` 的类型——**提取器**自动按参数解析。

### 提取器与请求参数

```rust
use axum::extract::{Path, Query, Json, State};
use serde::Deserialize;

#[derive(Deserialize)]
struct PageParams { page: Option<u32>, limit: Option<u32> }

// Path 路径参数 + Query 查询参数 + Json 请求体 + State 共享状态
async fn list_accounts(
    State(state): State<Arc<AppState>>,
    Query(params): Query<PageParams>,
) -> Result<Json<Vec<Account>>, ApiError> {
    let limit = params.limit.unwrap_or(20).min(100);
    // ...查询数据库...
    unimplemented!()
}
```

提取器**按参数顺序**生效，无副作用且组合自由，这是 axum 最优雅之处。顺序规则：`State`/`Path`/`Query`/`Json` 等在最后一个参数前的顺序有约定（`Json` 只能最后），实际项目中按惯例 `State, Path, Query, Json` 排列。

### 响应：自动 JSON

处理函数返回 `impl IntoResponse`。`Json<T>` 自动设置 `Content-Type: application/json`。用 `Result<T, E>` 让错误类型实现 `IntoResponse`，即可统一错误处理。

## 8.3 统一错误模型

定义 `ApiError` 并实现 `IntoResponse`，所有处理函数返回 `Result<_, ApiError>`，从任何内部错误自动转换：

```rust
use axum::{http::StatusCode, response::{IntoResponse, Response}, Json};
use serde_json::json;

pub enum ApiError {
    NotFound(String),
    BadRequest(String),
    Internal(anyhow::Error),
}

impl From<anyhow::Error> for ApiError {
    fn from(e: anyhow::Error) -> Self { ApiError::Internal(e) }
}

// 数据访问错误自动汇入统一错误（避免每处 map_err）。唯一约束冲突可映射 409。
impl From<sqlx::Error> for ApiError {
    fn from(e: sqlx::Error) -> Self {
        match &e {
            sqlx::Error::RowNotFound => ApiError::NotFound("记录不存在".into()),
            sqlx::Error::Database(db_err) if db_err.is_unique_violation() => {
                ApiError::BadRequest("违反唯一约束，数据已存在".into())
            }
            _ => ApiError::Internal(anyhow::anyhow!("数据库错误: {e}")),
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
```

处理函数内部用 `anyhow::Result` + `?` 传播，返回处 `map_err` 到 `ApiError`；或让 `anyhow::Error` 通过 `From` 自动进入 `ApiError::Internal`。**原则：业务错误转成明确状态码，未知错误记日志返回 500。**

## 8.4 SQLx：编译期校验 SQL

SQLx 是纯异步数据库驱动（非 ORM）。三种用法：

- **运行时字符串查询**：`sqlx::query(...)`（无编译期检查）；
- **编译期检查**：`query!`/`query_as!` 宏，需配置 `DATABASE_URL` 供 `cargo` 在编译时连库校验（`sqlx` CLI 或运行时）：
  ```rust
  // 该行 SQL 在编译期检查列名与类型
  let rows: Vec<Account> = sqlx::query_as::<_, Account>(
      "SELECT id, name, balance FROM accounts"
  ).fetch_all(&pool).await?;
  ```
- **迁移**：`sqlx::migrate!("./migrations").run(&pool).await?` 在启动时自动建表。

示例迁移 `migrations/0001_create_accounts.sql`：

```sql
CREATE TABLE IF NOT EXISTS accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    balance REAL NOT NULL DEFAULT 0
);
```

### 模型用 FromRow 自动映射

```rust
use serde::{Deserialize, Serialize};
use sqlx::FromRow;

#[derive(Debug, Serialize, Deserialize, FromRow, Clone)]
pub struct Account {
    pub id: i64,
    pub name: String,
    pub balance: f64,
}
```

> 生产建议：不要把 `f64` 存金额（精度问题），SQLite 示例用 `i64`（分为单位）更严谨；PostgreSQL 可用 `NUMERIC`。本课程聚焦通路，金额字段类型留作你的思考题。

## 8.5 数据访问（db.rs）

```rust
use sqlx::SqlitePool;
use crate::models::Account;
use crate::error::ApiError;

pub async fn init_db(database_url: &str) -> Result<SqlitePool, sqlx::Error> {
    let pool = SqlitePool::connect(database_url).await?;
    sqlx::migrate!("./migrations").run(&pool).await?;   // 自动建表
    Ok(pool)
}

pub async fn list_all(pool: &SqlitePool) -> Result<Vec<Account>, sqlx::Error> {
    sqlx::query_as::<_, Account>("SELECT id, name, balance FROM accounts ORDER BY id")
        .fetch_all(pool)
        .await
}

pub async fn get_one(pool: &SqlitePool, id: i64) -> Result<Option<Account>, sqlx::Error> {
    sqlx::query_as::<_, Account>("SELECT id, name, balance FROM accounts WHERE id = ?")
        .bind(id)
        .fetch_optional(pool)
        .await
}

pub async fn create(pool: &SqlitePool, name: &str, balance: f64) -> Result<Account, sqlx::Error> {
    sqlx::query_as::<_, Account>(
        "INSERT INTO accounts (name, balance) VALUES (?, ?) RETURNING id, name, balance",
    )
    .bind(name)
    .bind(balance)
    .fetch_one(pool)
    .await
}
```

> SQLite 支持 `RETURNING`，一条语句插入并取回行；`?` 为参数占位符（PostgreSQL/MySQL 用 `$1`）。

## 8.6 处理函数（handlers.rs）

```rust
use axum::extract::{Path, State};
use axum::Json;
use serde::Deserialize;
use std::sync::Arc;

use crate::db;
use crate::error::ApiError;
use crate::models::Account;

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
    if req.name.trim().is_empty() {
        return Err(ApiError::BadRequest("名称不能为空".into()));
    }
    let account = db::create(&state.pool, &req.name, req.balance).await?;
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
```

`AppState` 定义在 `main.rs` 或单独 `state.rs`，这里可 `use crate::AppState`。

## 8.7 main.rs：组装与启动

```rust
mod db;
mod error;
mod handlers;
mod models;

use std::sync::Arc;
use tokio::net::TcpListener;

#[derive(Clone)]
struct AppState { pool: sqlx::SqlitePool }

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt().with_env_filter("info").init();

    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "sqlite:accounts.db".to_string());
    let pool = db::init_db(&database_url).await?;
    let state = Arc::new(AppState { pool });

    let app = Router::new()
        .route("/ping", get(handlers::ping))
        .route("/accounts", get(handlers::list_accounts).post(handlers::create_account))
        .route("/accounts/{id}", get(handlers::get_account))
        .with_state(state)
        .layer(tower_http::trace::TraceLayer::new_for_http());   // 请求日志

    let listener = TcpListener::bind("0.0.0.0:8080").await?;
    tracing::info!("listening on {}", listener.local_addr()?);
    axum::serve(listener, app).await?;
    Ok(())
}
```

**要点**：`Route` 路径 `{id}`（0.7+ 语法）由 `Path<i64>` 解析；`tower_http` 提供 `TraceLayer` 等中间件（需 `features=["trace"]`）。

## 8.8 测试：用 tower 做 HTTP 级测试

不启动真实监听，把 `Router` 交给 `tower::ServiceExt::oneshot` 直接发请求（SQLite 用 `:memory:` 会因每个连接独立而失败，改用临时文件或 `sqlite::memory:` + `PoolOptions::max_connections(1)`）：

```rust
// tests/api_test.rs（或 main.rs 的 #[cfg(test)]）
use axum::body::Body;
use axum::http::{Request, StatusCode};
use tower::ServiceExt;   // oneshot

async fn build_test_app() -> (axum::Router, sqlx::SqlitePool) {
    let pool = sqlx::SqlitePool::connect("sqlite::memory:").await.unwrap();
    sqlx::query("CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, balance REAL NOT NULL DEFAULT 0)")
        .execute(&pool).await.unwrap();
    (build_app(pool.clone()), pool)
}

#[tokio::test]
async fn 创建账户成功() {
    let (app, _pool) = build_test_app().await;
    let res = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/accounts")
                .header("content-type", "application/json")
                .body(Body::from(r#"{"name":"奶茶","balance":100}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::CREATED);
}
```

## 8.9 实战：记账服务全链路验收

**需求**：完成第 8.1~8.7 的记账服务（账户 CRUD + 接口层 + SQLite 持久化）。

**验收清单**：

1. `cargo run` 后控制台出现 `listening on 0.0.0.0:8080`；
2. 用 curl/PowerShell 验证：
   ```powershell
   curl.exe -X POST http://localhost:8080/accounts -H "Content-Type: application/json" -d '{"name":"奶茶","balance":100}'
   curl.exe http://localhost:8080/accounts
   curl.exe http://localhost:8080/accounts/1
   curl.exe -i http://localhost:8080/accounts/999     # 期望 404 + 统一 JSON 错误
   ```
3. 空名称创建返回 400 与可读错误；
4. 请求 `/accounts/999` 返回 `{"error":"账户 999 不存在"}`（统一错误模型生效）；
5. `cargo test` 通过（含 HTTP 级测试）。

**扩展练习（记账进阶）**：

- 增加 `transactions` 表与"转账"接口：`POST /accounts/{id}/transfer {to_id, amount}`，用 `transaction`（`pool.begin()`）保证两账户余额原子变更；
- 给账户加 `created_at`（`sqlx::types::chrono`）；
- 引入 `tower_http` 的 `CorsLayer`/`RequestIdLayer`；
- 金额改为 `i64`（分为单位），比较与 `f64` 的差别；
- 用 `#[axum::debug_handler]` 观察处理函数编译错误提示。

> 关键收获：Axum 把"请求→处理→响应"收敛为**类型化的提取器与 IntoResponse**，SQLx 把 SQL 的可信度提前到编译期。整个服务没有魔法，靠组合就能扩展。下一章收尾：测试、质量门禁、容器化与发布——把"能跑"变成"可交付"。

上一章：[07-异步Rust与Tokio.md](./07-异步Rust与Tokio.md) ｜ 下一章：[09-测试质量与发布.md](./09-测试质量与发布.md) ｜ [返回 README](./README.md)
