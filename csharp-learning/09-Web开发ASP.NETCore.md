# 第九章 Web 开发：ASP.NET Core

> 本章目标：理解 HTTP 与 REST，用 **ASP.NET Core Minimal API** 从零做出一个带数据库的“待办 REST 服务”（增删改查 + Swagger 文档），并把第八章 EF Core 真正接入 Web。这是主线 9 章的收官交付点。
> 前置：第一至八章（重点 LINQ + EF + async）。

## 9.1 Web 服务一分钟入门

- **HTTP 协议**：客户端发“请求”（方法 + 路径 + 头 + 体），服务器回“响应”（状态码 + 体）；
- **REST**：把数据当“资源”，用 HTTP 动词表达操作：

| 动词 | 路径 | 含义 |
| --- | --- | --- |
| `GET` | `/api/todos` | 查询列表 |
| `POST` | `/api/todos` | 新增 |
| `GET` | `/api/todos/{id}` | 查单个 |
| `PUT` | `/api/todos/{id}` | 全量更新 |
| `PATCH` | `/api/todos/{id}` | 局部更新 |
| `DELETE` | `/api/todos/{id}` | 删除 |

状态码常识：`200` 成功、`201` 已创建、`204` 无内容、`400` 请求参数错、`404` 不存在、`500` 服务器错。

> 对照：Python FastAPI、Java Spring Boot 也是同一套 REST 思路。C# 的优势是性能高 + 全栈官方。

## 9.2 建工程与认识模板

```bash
dotnet new web -o TodoApi        # Minimal API 模板（最简 Web）
cd TodoApi
dotnet run                       # 默认监听 http://localhost:5xxx
# 浏览器打开 / 终端里 curl 一下根路径
```

生成的 `Program.cs` 就是整个应用的骨架：

```csharp
var builder = WebApplication.CreateBuilder(args);   // ① 构建“应用工厂”
var app = builder.Build();                          // ② 组装应用

app.MapGet("/", () => "Hello World!");              // ③ 定义路由

app.Run();                                          // ④ 跑起来
```

**Hosting 模型**：`builder` 里配置服务（数据库、日志、认证……）→ `app` 里配置中间件与路由 → 启动监听。

## 9.3 一个带数据的完整 Todo 服务

### 9.3.1 建工程并加包

```bash
dotnet new web -o TodoApi
cd TodoApi
dotnet add package Microsoft.EntityFrameworkCore.Sqlite
dotnet add package Microsoft.EntityFrameworkCore.Design
```

### 9.3.2 模型与 DbContext

```csharp
// Models.cs
public class Todo
{
    public int Id { get; set; }
    public string Title { get; set; } = "";
    public bool IsDone { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.Now;
}
```

```csharp
// TodoDb.cs
using Microsoft.EntityFrameworkCore;

public class TodoDb : DbContext
{
    public TodoDb(DbContextOptions<TodoDb> options) : base(options) { }
    public DbSet<Todo> Todos => Set<Todo>();
}
```

### 9.3.3 Program.cs：注册服务 + 路由

```csharp
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

// —— 注册服务：把 DbContext 交给依赖注入容器（连接串来自 appsettings.json）——
var conn = builder.Configuration.GetConnectionString("Todo") ?? "Data Source=todo.db";
builder.Services.AddDbContext<TodoDb>(o => o.UseSqlite(conn));

// 学习期自动建表（正式应使用 EF 迁移）
builder.Services.AddScoped(sp =>
{
    var db = sp.GetRequiredService<TodoDb>();
    db.Database.EnsureCreated();
    return db;
});

// Swagger/OpenAPI：接口文档（加包 Microsoft.AspNetCore.OpenApi）
builder.Services.AddOpenApi();

var app = builder.Build();

app.MapOpenApi();          // 暴露 /openapi/v1.json

// —— 路由：Todo 资源 CRUD ——

// 1. 查列表（支持 ?done=true 过滤）
app.MapGet("/api/todos", async (TodoDb db, bool? done) =>
{
    var q = db.Todos.AsQueryable();
    if (done is not null) q = q.Where(t => t.IsDone == done);
    return Results.Ok(await q.OrderByDescending(t => t.Id).ToListAsync());
});

// 2. 查单个
app.MapGet("/api/todos/{id:int}", async (TodoDb db, int id) =>
    await db.Todos.FindAsync(id) is Todo t ? Results.Ok(t) : Results.NotFound());

// 3. 新增
app.MapPost("/api/todos", async (TodoDb db, Todo input) =>
{
    if (string.IsNullOrWhiteSpace(input.Title)) return Results.BadRequest("标题不能为空");
    db.Todos.Add(input);
    await db.SaveChangesAsync();
    return Results.Created($"/api/todos/{input.Id}", input);
});

// 4. 全量更新
app.MapPut("/api/todos/{id:int}", async (TodoDb db, int id, Todo input) =>
{
    var todo = await db.Todos.FindAsync(id);
    if (todo is null) return Results.NotFound();
    if (string.IsNullOrWhiteSpace(input.Title)) return Results.BadRequest("标题不能为空");
    todo.Title = input.Title;
    todo.IsDone = input.IsDone;
    await db.SaveChangesAsync();
    return Results.Ok(todo);
});

// 5. 删除
app.MapDelete("/api/todos/{id:int}", async (TodoDb db, int id) =>
{
    var todo = await db.Todos.FindAsync(id);
    if (todo is null) return Results.NotFound();
    db.Todos.Remove(todo);
    await db.SaveChangesAsync();
    return Results.NoContent();
});

app.Run();
```

### 9.3.4 跑起来测试

```bash
dotnet run
# 另开一个终端
curl -s http://localhost:PORT/api/todos
curl -s -X POST http://localhost:PORT/api/todos -H "Content-Type: application/json" -d '{"title":"学 C#","isDone":false}'
curl -s -X PUT  http://localhost:PORT/api/todos/1 -H "Content-Type: application/json" -d '{"title":"学 C#（已完成）","isDone":true}'
curl -s -X DELETE http://localhost:PORT/api/todos/1
```

Windows PowerShell 里用 `curl.exe`（注意别名）或用浏览器/Postman。Swagger 文档地址：`/openapi/v1.json`（装了 SwaggerUI 包后可看可视化页面）。

## 9.4 依赖注入（DI）：理解 Web 应用的地基

9.3.3 里 `app.MapGet(..., async (TodoDb db, ...) => ...)` 的参数 `TodoDb db` 是怎么来的？——**依赖注入容器自动创建**（路由处理器的参数自动解析）。

- 注册生命周期：`AddSingleton`（全进程一个）/ `AddScoped`（每个请求一个）/ `AddTransient`（每次要一个）；
- EF `DbContext` 必须 `AddScoped`（一次请求一个上下文，避免并发冲突与连接泄漏）；
- 好处：解耦、易测试（第 10 章注入测试替身）。

```csharp
// 把“业务服务”也交给 DI（好习惯：路由薄、服务层做业务）
builder.Services.AddScoped<TodoService>();

// TodoService.cs
public class TodoService(TodoDb db)          // C# 12 主构造器
{
    public async Task<Todo> AddAsync(string title)
    {
        var t = new Todo { Title = title };
        db.Todos.Add(t);
        await db.SaveChangesAsync();
        return t;
    }
    // 更多方法...
}
// 路由里：app.MapPost("/api/todos", async (TodoService svc, CreateTodo dto) => ...);
```

## 9.5 配置与日志（Web 版）

### 9.5.1 配置：appsettings.json

```json
{
  "Logging": {
    "LogLevel": { "Default": "Information" }
  },
  "ConnectionStrings": {
    "Todo": "Data Source=todo.db"
  },
  "App": {
    "PageSize": 20
  }
}
```

```csharp
string? cs = builder.Configuration.GetConnectionString("Todo");
int pageSize = builder.Configuration.GetValue<int>("App:PageSize");
var connStr = builder.Configuration["App:Secret"];   // 生产环境用环境变量覆盖
```

### 9.5.2 日志：内置 ILogger

```csharp
app.MapPost("/api/todos", async (TodoDb db, ILogger<Program> logger, Todo input) =>
{
    logger.LogInformation("新增待办：{Title}", input.Title);   // 结构化日志
    try { /* ... */ }
    catch (Exception ex)
    {
        logger.LogError(ex, "新增待办失败：{Title}", input.Title);
        return Results.Problem("服务器内部错误");
    }
});
```

## 9.6 DTO 与输入验证

把“请求体”与“数据库实体”分开是好实践（防过度暴露字段、防批量赋值漏洞）：

```csharp
// DTO：客户端只能传这两个字段
public record CreateTodoDto(string? Title, bool IsDone);

app.MapPost("/api/todos", async (TodoDb db, CreateTodoDto dto) =>
{
    if (string.IsNullOrWhiteSpace(dto.Title)) return Results.BadRequest("标题不能为空");
    var todo = new Todo { Title = dto.Title.Trim(), IsDone = dto.IsDone };
    db.Todos.Add(todo);
    await db.SaveChangesAsync();
    return Results.Created($"/api/todos/{todo.Id}", todo);
});
```

> Minimal API 还能用 **验证器**（`Microsoft.AspNetCore.Http.Validation` 或 FluentValidation）做更复杂校验，入门阶段先学会“请求→DTO→校验→实体”。

## 9.7 中间件与 HTTPS 重定向

```csharp
var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();                    // 开发环境才暴露接口文档
}

app.UseHttpsRedirection();               // HTTP 请求重定向到 HTTPS（模板自带，可先注释）

// 自定义中间件：记录每个请求耗时（体验“管道”概念）
app.Use(async (context, next) =>
{
    var sw = System.Diagnostics.Stopwatch.StartNew();
    await next();
    sw.Stop();
    Console.WriteLine($"{context.Request.Method} {context.Request.Path} -> {context.Response.StatusCode} ({sw.ElapsedMilliseconds}ms)");
});
```

中间件按注册顺序构成管道，每个请求都流经管道。后续加 `Authentication`/`Authorization`、异常处理中间件都在这层。

## 9.8 发布与运行

```bash
dotnet publish -c Release -o ./publish        # 发布（自包含可加 -r win-x64 等）
cd publish
./TodoApi                                     # 直接运行（已带全部依赖）
```

也支持 Docker（第 10 章给多阶段 Dockerfile）、部署到 Linux/Azure/云服务器。跨平台是 ASP.NET Core 的一大卖点。

## 9.9 完整示例：把 9.3 的 Todo 升级成“分类 + 多 Todo”

练习把第八章的一对多关系搬进 Web：

```csharp
// Models.cs
public class Category
{
    public int Id { get; set; }
    public string Name { get; set; } = "";
    public List<Todo> Todos { get; set; } = new();
}
public class Todo
{
    public int Id { get; set; }
    public string Title { get; set; } = "";
    public bool IsDone { get; set; }
    public int CategoryId { get; set; }
    public Category? Category { get; set; }
}
```

```csharp
// Program.cs 片段：按分类查 + 查分类统计
app.MapGet("/api/categories", async (TodoDb db) =>
    await db.Categories
        .Select(c => new { c.Id, c.Name, Count = c.Todos.Count(t => !t.IsDone) })
        .ToListAsync());

app.MapGet("/api/categories/{id:int}/todos", async (TodoDb db, int id) =>
{
    var exist = await db.Categories.FindAsync(id);
    if (exist is null) return Results.NotFound();
    return Results.Ok(await db.Todos.Where(t => t.CategoryId == id).ToListAsync());
});

app.MapPost("/api/categories/{id:int}/todos", async (TodoDb db, int id, CreateTodoDto dto) =>
{
    if (string.IsNullOrWhiteSpace(dto.Title)) return Results.BadRequest("标题不能为空");
    var todo = new Todo { Title = dto.Title, IsDone = dto.IsDone, CategoryId = id };
    db.Todos.Add(todo);
    await db.SaveChangesAsync();
    return Results.Created($"/api/todos/{todo.Id}", todo);
});
```

## 9.10 实战与验收

**必做**

1. 建 `TodoApi` 工程并跑通 9.3 全部路由，用 curl/浏览器验证增删改查；
2. 实现“分类”模型 + 9.9 的按分类查询与新增；
3. 写一个“状态标记完成”的接口：`POST /api/todos/{id}/done`（用 `IsDone=true` + SaveChanges）；
4. 增加校验：标题超 50 字返回 400；不存在的分类 Id 返回 404。

**进阶题（任选）**

- 给 `/api/todos` 加分页参数 `page` / `pageSize`（Skip/Take）；
- 用 `ILogger` 记录“创建/删除”关键操作，观察控制台日志；
- 尝试把存储层从 SQLite 换成 PostgreSQL（换连接串与包）——体会 EF Core 多数据库能力。

**验收清单**

- [ ] 理解 HTTP 动词与状态码的对应
- [ ] 能解释 `builder` 注册服务与路由中参数注入的关系
- [ ] 会写 Minimal API 的 CRUD 与参数约束 `{id:int}`
- [ ] 会用 DTO + 校验保护接口
- [ ] 看懂 `app.Use` 中间件管道
- [ ] 跑通一个带 EF Core + SQLite 的 REST 服务并完成发布

## 9.11 小结与主线总结

主线 9 章到此收官：**语法 → 面向对象 → 类型系统 → 集合/LINQ → 错误与调试 → 异步并发 → .NET 类库 → EF Core → ASP.NET Core**。你已经能：
从零建一个带数据库、带校验、带日志、能发布的 REST 服务——这正是 Java Spring Boot / Python FastAPI / Rust Axum 工程师做的工作，而 C# 的版本以官方全家桶 + 高性能著称。

接下来的进阶部分：
- 第十章把工程经验补全：**测试、CI、Docker**；
- 第十一章钻到底层：**内存模型、Span、unsafe、与 C 互操作**；
- 第十二章看向全景：**桌面/游戏/云/AI/大数据生态**与最终对照。

## 语言对照

| 概念 | C# | Java | Python | Rust |
| --- | --- | --- | --- | --- |
| Web 框架 | ASP.NET Core | Spring Boot | FastAPI | Axum |
| 项目脚手架 | `dotnet new web` | start.spring.io | FastAPI 手搭 | `cargo new` + 依赖 |
| DI 容器 | `builder.Services` | Spring IoC | 无/Depends | 无（Axum state） |
| 路由 | `app.MapGet(...)` | `@GetMapping` | `@app.get` | `router.get` |
| 配置 | appsettings.json | application.yml | .env/pydantic | config 类库 |
| 接口文档 | OpenAPI/Swagger | springdoc | FastAPI 自动 | utoipa |
| ORM 集成 | EF Core | MyBatis/JPA | SQLAlchemy | sqlx |
| 日志 | `ILogger`/Serilog | SLF4J | logging | tracing |

下一章：[10-测试质量与发布.md](./10-测试质量与发布.md) ｜ [返回 README](./README.md)
