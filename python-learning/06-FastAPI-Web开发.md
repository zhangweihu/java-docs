# 第六章 FastAPI Web 开发：对标 Spring Boot 快速上手

> 本章目标：用 **FastAPI** 快速搭建现代 Python Web 服务——这是 Python 生态里**最像 Spring Boot** 的框架。全程对照 Spring Boot 逐项迁移：路由（@app.get ↔ @GetMapping）、数据校验（Pydantic ↔ Bean Validation）、依赖注入（Depends ↔ @Autowired）、ORM（SQLAlchemy ↔ MyBatis-Plus/JPA）、中间件、异常处理、API 文档（自动 Swagger）。学完能独立开发一个带数据库的 RESTful 服务并部署上线。
>
> 前置知识：第一章~第三章（Python 基础、装饰器、类型注解）、第四章（asyncio）。建议对照 java-learning 的 Spring Boot 章节学习。
>
> 环境准备：`pip install fastapi "uvicorn[standard]"`，SQLAlchemy 相关在 6.5 节再装。

## 6.1 为什么选 FastAPI：Python Web 框架三选一

| 框架 | 特点 | 适合 | 对标 |
| --- | --- | --- | --- |
| **Flask** | 轻量、灵活、老牌 | 小服务、自定义强 | Spring MVC 裸配 |
| **Django** | 全家桶（自带 ORM/Admin/模板） | 内容型网站、快速全栈 | Spring Boot 全家桶 |
| **FastAPI** | 异步高性能、自动文档、类型安全 | **API 服务**、微服务、AI 后端 | Spring Boot + OpenAPI |

**为什么后端/微服务场景优先 FastAPI？**

1. **性能**：基于 ASGI + Starlette，异步原生，性能接近 Node.js/Go；
2. **类型安全**：基于 Python 类型注解自动做参数校验和序列化（Pydantic）；
3. **自动 API 文档**：`/docs`（Swagger UI）和 `/redoc` 开箱即用，接口文档零成本；
4. **依赖注入内置**：`Depends` 与 Spring 的依赖注入理念同源但更轻量；
5. **AI 生态标配**：LangChain、向量数据库等 AI 后端几乎都用 FastAPI 做服务层。

### 6.1.1 最小可运行服务（30 秒上手）

```python
# main.py
from fastapi import FastAPI

app = FastAPI(title="我的第一个 API")

@app.get("/")
def root():
    return {"message": "Hello FastAPI"}
```

```bash
uvicorn main:app --reload --port 8000
# main: 模块名   app: FastAPI 实例名   --reload: 改代码自动重启（开发用）
```

访问验证三件套：

```
http://127.0.0.1:8000/           →  {"message": "Hello FastAPI"}
http://127.0.0.1:8000/docs       → Swagger UI 交互式文档（自动生成！）
http://127.0.0.1:8000/openapi.json → OpenAPI 规范（对接前端/客户端自动生成）
```

> **对标 Spring Boot**：`uvicorn main:app` ≈ `mvn spring-boot:run`；`/docs` ≈ Springdoc/knife4j 配置完才有的 Swagger——FastAPI 是**零配置自带**。

## 6.2 路由与参数：对标 @Controller/@RestController

### 6.2.1 路由装饰器

```python
from fastapi import FastAPI

app = FastAPI()

@app.get("/users")              # GET   对应 @GetMapping("/users")
def list_users(): ...

@app.post("/users")             # POST  对应 @PostMapping
def create_user(): ...

@app.put("/users/{user_id}")    # PUT   路径参数
def update_user(user_id: int): ...

@app.delete("/users/{user_id}") # DELETE
def delete_user(user_id: int): ...
```

### 6.2.2 三类参数：路径 / 查询 / 请求体

```python
from fastapi import FastAPI, Path, Query, Body

app = FastAPI()

# 1. 路径参数（Path）：/users/42
@app.get("/users/{user_id}")
def get_user(
    user_id: int = Path(ge=1),                  # 类型 + 校验：必须 >= 1
):
    return {"user_id": user_id}

# 2. 查询参数（Query）：/search?keyword=py&page=2&size=10
@app.get("/search")
def search(
    keyword: str = Query(min_length=2, max_length=50),   # 必填 + 长度校验
    page: int = Query(1, ge=1),                          # 默认 1，最小 1
    size: int = Query(10, ge=1, le=100),
):
    return {"keyword": keyword, "page": page, "size": size}

# 3. 请求体（Body）：POST 时 JSON 主体，见 6.3 Pydantic
@app.post("/books")
def create_book(book: dict = Body(...)):
    return {"received": book}
```

**关键点：参数默认值决定"必填 or 可选"。** 没有默认值 = 必填；有默认值 = 可选（默认生效）。

## 6.3 Pydantic 数据校验：对标 Bean Validation + Jackson

Pydantic 是 FastAPI 的"数据模型层"：**声明模型 → 自动校验 → 自动序列化**，一套东西同时替代了 Spring 的 `@Validated` 注解校验和 Jackson 的 JSON 转换。

### 6.3.1 定义模型与自动校验

```python
from pydantic import BaseModel, Field, EmailStr

class UserCreate(BaseModel):                    # 对应 @Data 的 CreateDTO
    name: str = Field(min_length=2, max_length=20)          # @NotBlank + @Size
    email: EmailStr                              # @Email
    age: int = Field(ge=0, le=150)               # @Min @Max
    tags: list[str] = []                         # 默认空列表
    vip: bool = False

@app.post("/users")
def create_user(user: UserCreate):              # 参数是 Pydantic 模型 → 自动校验 + 注入
    return {"name": user.name, "email": user.email, "age": user.age}
```

传非法数据（如 `age=-1`、`email="abc"`），FastAPI 自动返回 **422 校验错误**：

```json
{
  "detail": [
    {"loc": ["body", "age"], "msg": "Input should be greater than or equal to 0", "type": "greater_than_equal"},
    {"loc": ["body", "email"], "msg": "value is not a valid email address", "type": "value_error"}
  ]
}
```

> **对标 Spring**：上面的校验逻辑在 Spring 里要写 `@NotBlank` + `@Size` + `@Email` + `@Min` + `@Max` 五个注解 + 一个 `@Valid` 才能触发，而且校验错误要自己配异常处理器。FastAPI **声明即校验，错误自动规范化返回**。

### 6.3.2 响应模型与字段控制

```python
from pydantic import BaseModel, ConfigDict

class UserOut(BaseModel):                        # 输出模型（对应 Response DTO）
    model_config = ConfigDict(from_attributes=True)   # 允许从 ORM 对象直接转换
    id: int
    name: str
    email: EmailStr
    # 不写 password 字段 → 响应里永远不会出现密码（天然防泄漏）

@app.post("/users", response_model=UserOut, status_code=201)   # 指定响应模型 + 状态码
def create_user(user: UserCreate):
    # 业务里创建用户...返回 UserOut（多出来的字段会被过滤掉）
    return {"id": 1, "name": user.name, "email": user.email, "password": "secret"}
# 响应里只有 id/name/email，password 被自动剔除
```

| Pydantic 特性 | 对标 Spring |
| --- | --- |
| `Field(min_length=...)` | `@NotBlank` `@Size` `@Min` `@Max` |
| `EmailStr` / 类型约束 | `@Email` / `@Pattern` |
| `response_model` 过滤输出字段 | DTO 设计（防止泄漏密码等敏感字段） |
| 默认值/可选字段 | `@Builder.Default` |
| 自动 422 错误 | `MethodArgumentNotValidException` 处理器 |

## 6.4 依赖注入与中间件

### 6.4.1 Depends：对标 @Autowired（但更显式）

```python
from fastapi import FastAPI, Depends, Header, HTTPException

app = FastAPI()

# 1. 定义一个"依赖"：数据库连接 / 用户认证 / 通用参数
def get_db():
    db = connect_to_db()
    try:
        yield db                # yield = 用完后自动执行清理（对标 @PreDestroy）
    finally:
        db.close()

# 2. 一个"当前用户"依赖（认证逻辑复用）
def get_current_user(authorization: str = Header(...)):
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="未登录")
    return {"username": "alice"}

# 3. 在路由里声明依赖 → FastAPI 自动调用并注入
@app.get("/profile")
def profile(user: dict = Depends(get_current_user)):
    return {"欢迎": user["username"]}

@app.post("/users")
def create_user(db = Depends(get_db)):           # db 自动注入、用后自动关闭
    return {"db": "已连接"}
```

**依赖可以叠加、可以缓存**：`Depends(get_db)` 在同一个请求内多次声明只会执行一次（依赖缓存）。这就是 Spring 容器注入的"轻量版"——FastAPI 用函数而不是容器，更直观。

### 6.4.2 中间件（Middleware）

```python
import time
from fastapi import FastAPI, Request

app = FastAPI()

@app.middleware("http")                  # 对标 OncePerRequestFilter / 拦截器
async def add_process_time(request: Request, call_next):
    start = time.perf_counter()
    response = await call_next(request)  # 放行到下一个环节
    response.headers["X-Process-Time"] = f"{time.perf_counter() - start:.4f}s"
    return response
```

**统一异常处理**（对标 @RestControllerAdvice）：

```python
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError

@app.exception_handler(RequestValidationError)
async def validation_handler(request, exc):
    return JSONResponse(status_code=422, content={"code": 422, "msg": "参数错误", "detail": exc.errors()})
```

## 6.5 数据库：SQLAlchemy（对标 MyBatis-Plus / JPA）

```bash
pip install sqlalchemy pymysql
```

### 6.5.1 连接与模型定义

```python
# database.py —— 对标 application.yml 里的数据源配置
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, DeclarativeBase

# 连接串：mysql+pymysql://用户:密码@主机:端口/库名?charset=utf8mb4
DATABASE_URL = "mysql+pymysql://root:123456@127.0.0.1:3306/fastapi_demo?charset=utf8mb4"

engine = create_engine(DATABASE_URL, pool_size=10, pool_recycle=3600)   # 连接池（对标 HikariCP）
SessionLocal = sessionmaker(bind=engine, autoflush=False)

class Base(DeclarativeBase):               # 模型基类（对标 @MappedSuperclass）
    pass
```

```python
# models.py —— 表模型（对标 @TableName/@TableId 的实体类）
from datetime import datetime
from sqlalchemy import String, Integer, DateTime, Boolean, func
from sqlalchemy.orm import Mapped, mapped_column
from database import Base

class User(Base):
    __tablename__ = "users"                # 表名

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(50), unique=True, index=True)
    email: Mapped[str] = mapped_column(String(100))
    age: Mapped[int] = mapped_column(Integer, default=0)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    create_time: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
```

### 6.5.2 CRUD 路由（完整示例）

```python
# main.py —— 用户 CRUD 接口（对照 Java 的 Service + Controller）
from datetime import datetime
from fastapi import FastAPI, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select, func
from sqlalchemy.orm import Session

from database import SessionLocal, Base, engine
from models import User

Base.metadata.create_all(bind=engine)      # 建表（开发用；生产用迁移工具，见 java-learning 38 章）

app = FastAPI()

def get_db():                              # 依赖：每个请求一个 Session
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

class UserCreate(BaseModel):               # 入参模型
    name: str
    email: str
    age: int = 0

class UserOut(BaseModel):                  # 出参模型（隐藏敏感字段）
    id: int
    name: str
    email: str
    age: int
    create_time: datetime
    model_config = {"from_attributes": True}

# ─── 增 ───
@app.post("/users", response_model=UserOut, status_code=201)
def create_user(data: UserCreate, db: Session = Depends(get_db)):
    if db.execute(select(User).where(User.name == data.name)).scalar_one_or_none():
        raise HTTPException(status_code=400, detail="用户名已存在")
    user = User(**data.model_dump())
    db.add(user)
    db.commit()                            # 事务提交
    db.refresh(user)                       # 刷新出自增 id
    return user

# ─── 查（单个）───
@app.get("/users/{user_id}", response_model=UserOut)
def get_user(user_id: int, db: Session = Depends(get_db)):
    user = db.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")
    return user

# ─── 改 ───
@app.put("/users/{user_id}", response_model=UserOut)
def update_user(user_id: int, data: UserCreate, db: Session = Depends(get_db)):
    user = db.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")
    for key, value in data.model_dump().items():
        setattr(user, key, value)
    db.commit()
    db.refresh(user)
    return user

# ─── 删 ───
@app.delete("/users/{user_id}", status_code=204)
def delete_user(user_id: int, db: Session = Depends(get_db)):
    user = db.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")
    db.delete(user)
    db.commit()

# ─── 查（分页）───
@app.get("/users")
def list_users(page: int = 1, size: int = 10, db: Session = Depends(get_db)):
    total = db.scalar(select(func.count()).select_from(User))
    users = db.scalars(select(User).offset((page - 1) * size).limit(size)).all()
    return {"total": total, "items": users}
```

> **对标 Spring**：`Depends(get_db)` ≈ `@Autowired` + 事务模板；`HTTPException(404)` ≈ `throw new ResponseStatusException(HttpStatus.NOT_FOUND)`；`response_model` ≈ DTO 映射。SQLAlchemy 的 `Session` ≈ JPA 的 `EntityManager` / MyBatis 的 `SqlSession`。

### 6.5.3 建表与迁移

- 开发期：`Base.metadata.create_all()` 一句建表（只建不更新，改表要删库重来）；
- 生产期：**必须用迁移工具**（对齐 java-learning 第 38 章理念，Python 生态对应 **Alembic**）：

```bash
pip install alembic
alembic init alembic          # 生成迁移目录
alembic revision --autogenerate -m "create users table"   # 自动生成迁移脚本
alembic upgrade head          # 应用迁移
```

## 6.6 异常处理与日志

### 6.6.1 统一异常结构（对标 @RestControllerAdvice）

```python
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

app = FastAPI()

class BizException(Exception):               # 自定义业务异常（对标自定义 RuntimeException）
    def __init__(self, code: int, msg: str):
        self.code = code
        self.msg = msg

@app.exception_handler(BizException)
async def biz_exception_handler(request: Request, exc: BizException):
    return JSONResponse(status_code=exc.code, content={"code": exc.code, "msg": exc.msg})

@app.get("/order/{order_id}")
def get_order(order_id: int):
    if order_id <= 0:
        raise BizException(400, "订单号不合法")
    return {"order_id": order_id}
```

### 6.6.2 日志配置（对接第三章 logging）

```python
import logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("api")

@app.get("/ping")
def ping():
    logger.info("收到 ping 请求")
    return {"pong": True}
```

## 6.7 部署：从开发到上线

### 6.7.1 生产启动（多进程）

```bash
# 开发：单进程热重载（不要用于生产）
uvicorn main:app --reload --port 8000

# 生产：4 个 worker 进程（对标 Tomcat 多线程，但这里是多进程 + 内部异步）
uvicorn main:app --host 0.0.0.0 --port 8000 --workers 4
```

### 6.7.2 Docker 部署（对标 Spring Boot 镜像）

```dockerfile
# Dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]
```

```bash
docker build -t my-api .
docker run -d -p 8000:8000 --name my-api my-api
```

### 6.7.3 与 Nginx 搭配（对标网关）

```
浏览器 ──► Nginx(80/443, 静态资源/SSL/负载均衡) ──► uvicorn:8000 ──► MySQL
```

```nginx
# nginx.conf 关键片段
location /api/ {
    proxy_pass http://127.0.0.1:8000;      # 反代到 FastAPI
    proxy_set_header Host $host;
}
```

## 6.8 实战：图书管理系统 API（综合实战）

把第五章爬到的书数据 + FastAPI 串起来，做一个完整的"图书管理服务"：

```python
# books_api.py —— 图书 CRUD + 爬虫数据导入 + 搜索
import csv
import logging
from datetime import datetime
from typing import Optional

from fastapi import FastAPI, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, select, func
from sqlalchemy.orm import sessionmaker, DeclarativeBase, Session, Mapped, mapped_column
from sqlalchemy import String, Integer, Float

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s: %(message)s")
logger = logging.getLogger("books")

DATABASE_URL = "sqlite:///books.db"          # 演示用 SQLite，零依赖
engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(bind=engine)

class Base(DeclarativeBase):
    pass

class Book(Base):
    __tablename__ = "books"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    title: Mapped[str] = mapped_column(String(100), index=True)
    author: Mapped[str] = mapped_column(String(50))
    price: Mapped[float] = mapped_column(Float, default=0)
    rating: Mapped[int] = mapped_column(Integer, default=0)
    link: Mapped[str] = mapped_column(String(200), default="")
    create_time: Mapped[datetime] = mapped_column(server_default=func.now())

Base.metadata.create_all(bind=engine)

app = FastAPI(title="图书管理系统")

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

class BookCreate(BaseModel):
    title: str = Field(min_length=1, max_length=100)
    author: str = Field(min_length=1, max_length=50)
    price: float = Field(ge=0)
    rating: int = Field(default=0, ge=0, le=5)
    link: str = ""

class BookOut(BaseModel):
    id: int
    title: str
    author: str
    price: float
    rating: int
    create_time: datetime
    model_config = {"from_attributes": True}

@app.post("/books", response_model=BookOut, status_code=201)
def create_book(data: BookCreate, db: Session = Depends(get_db)):
    book = Book(**data.model_dump())
    db.add(book)
    db.commit()
    db.refresh(book)
    logger.info(f"新增图书: {book.title}")
    return book

@app.get("/books", response_model=list[BookOut])
def list_books(
    keyword: Optional[str] = Query(None, min_length=1),
    min_price: float = 0,
    page: int = Query(1, ge=1),
    size: int = Query(10, ge=1, le=100),
    db: Session = Depends(get_db),
):
    stmt = select(Book)
    if keyword:
        stmt = stmt.where(Book.title.contains(keyword))
    stmt = stmt.where(Book.price >= min_price).order_by(Book.price.desc())
    books = db.scalars(stmt.offset((page - 1) * size).limit(size)).all()
    return books

@app.post("/books/import-csv")
def import_books_from_csv(file_path: str, db: Session = Depends(get_db)):
    """把 5.5 节爬虫生成的 top_books.csv 导入数据库"""
    count = 0
    with open(file_path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            db.add(Book(title=row["title"], price=float(row["price"]),
                        rating=int(row["rating"]), link=row["link"]))
            count += 1
    db.commit()
    return {"imported": count}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8000, reload=True)
```

```bash
python books_api.py
# 打开 http://127.0.0.1:8000/docs 即可看到全部接口文档，逐个试调
```

**这个实战串联了全章**：Pydantic 校验、CRUD、分页搜索、CSV 导入（复用第五章产出）、日志、自动文档。

## 6.9 练习与总结

### 练习题（动手做）

1. 写一个 `/hello/{name}` 接口，路径参数 + 查询参数（age）都有，返回问候语；
2. 给用户接口加校验：手机号字段必须是 11 位数字（`Field(pattern=r"1\d{10}")`），测试 422 返回；
3. 为 6.8 的图书接口增加 `PUT /books/{id}`（修改）和 `DELETE /books/{id}`；
4. 用 `response_model` 隐藏 `password` 字段，验证响应中确实不出现；
5. 加一个中间件打印每个请求的耗时，并返回 `X-Process-Time` 响应头；
6. 用 `Depends` 写一个简单的 API Key 认证：请求头带 `X-API-Key`，不对则 401；
7. 把 6.8 的 SQLite 换成 MySQL（改 `DATABASE_URL` 为 `mysql+pymysql://...`），体验连接串差异；
8. 写一个 Dockerfile 并成功 `docker build` + `docker run` 起来访问 `/docs`。

### 面试高频题

| 问题 | 回答要点 |
| --- | --- |
| FastAPI vs Flask vs Django？ | FastAPI 异步高性能+类型安全+自动文档；Flask 轻量灵活；Django 全家桶适合内容站 |
| FastAPI 为什么快？ | ASGI 异步原生 + Starlette 内核；对比 WSGI 同步框架（Flask）高并发下有数量级优势 |
| Pydantic 的作用？ | 数据校验 + 序列化 + 类型转换，替代 Bean Validation + Jackson；422 自动错误响应 |
| 路径参数和查询参数区别？ | 路径参数在 URL 路径（/users/42）；查询参数在 ?a=1&b=2；都做类型声明与校验 |
| 必填参数怎么写？ | 参数无默认值 = 必填；`...`（Ellipsis）显式必填；有默认值 = 可选 |
| response_model 有什么用？ | 过滤输出字段（防密码泄漏）、类型转换、接口文档自动更新 |
| Depends 对标 Spring 什么？ | @Autowired 依赖注入，但用函数显式声明；支持 yield 做资源清理（对标 @PreDestroy） |
| 中间件怎么用？ | @app.middleware("http")，参数 call_next 放行；对标过滤器/拦截器 |
| FastAPI 怎么连数据库？ | SQLAlchemy ORM，Session 对标 EntityManager；连接串 mysql+pymysql://... |
| 分页查询怎么做？ | offset((page-1)*size).limit(size) + 单独 count 查询 |
| HTTPException 用法？ | 抛异常替代返回错误：HTTPException(status_code=404, detail="...") |
| 自定义异常怎么处理？ | 定义 Exception 子类 + @app.exception_handler 统一处理（对标 @RestControllerAdvice） |
| 生产怎么部署？ | uvicorn 多 worker + Docker + Nginx 反代；生产禁用 --reload |
| 自动文档怎么来的？ | 类型注解 → OpenAPI 规范 → /docs Swagger UI；无需额外配置 |
| 与 Spring Boot 怎么选？ | 两者可并存：Spring 强在生态与 Java 团队，FastAPI 强在快速迭代与 AI/数据分析衔接 |

### 本章小结

- **框架选型**（6.1）：Flask 轻量 / Django 全家桶 / FastAPI 高性能 API，后端优先 FastAPI；
- **路由与参数**（6.2）：`@app.get/post/put/delete`，路径/查询/请求体三类参数，默认值决定必填；
- **Pydantic**（6.3）：声明即校验（422 自动错误）、`response_model` 过滤敏感字段、对标 Bean Validation；
- **依赖注入与中间件**（6.4）：`Depends`（yield 清理）、`@app.middleware`、统一异常处理；
- **SQLAlchemy**（6.5）：模型 ↔ 表、Session 增删改查、分页、Alembic 迁移；
- **部署**（6.7）：uvicorn 多 worker + Docker + Nginx；
- **实战**（6.8）：图书管理 API 串联全章，可直接运行并支持 `/docs` 在线调试。

配套：第五章（爬虫数据 → CSV → 本章导入接口）、第四章（asyncio 与 FastAPI 异步同源）、第三章（logging/配置）。

下一章：[07-数据分析.md](./07-数据分析.md)（NumPy、Pandas 数据处理、Matplotlib 可视化）
