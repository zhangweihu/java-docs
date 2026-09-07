# 第八章 数据库与 EF Core

> 本章目标：理解关系数据库与 ORM，学会用 **EF Core** + SQLite 完成建表、增删改查、关系映射、迁移与事务，把“数据”持久化到真正的数据库。
> 前置：第一至七章（重点是第四章 LINQ——EF Core 的查询语法就是 LINQ）。

## 8.1 为什么要用数据库 + ORM

前几章数据都在内存里，程序一关就没了。真实系统需要**持久化**到数据库。数据库有很多种，本章用最省事的 **SQLite**（单文件、无需安装服务）。

- **关系型数据库**：表（Table）= 一类实体，行 = 一条记录，列 = 字段；表之间用外键关联；
- **ORM（对象关系映射）**：把“类 ↔ 表、对象 ↔ 行”自动转换，让你用 C# 对象操作数据库，少写 SQL；
- **EF Core**：.NET 官方 ORM（对标 Java 的 JPA/Hibernate、Python 的 SQLAlchemy）。你的 LINQ 查询会被翻译成 SQL 执行。

```text
C# 对象（Book b = ...）  ──EF Core──►  SQL  ──►  SQLite 数据库文件
```

## 8.2 环境准备与建工程

```bash
# 建一个控制台工程（用于学 EF；第 9 章会升级成 Web 工程）
dotnet new console -o BookStore
cd BookStore

# 加包：EF Core 的 SQLite 驱动 + 设计工具
dotnet add package Microsoft.EntityFrameworkCore.Sqlite
dotnet add package Microsoft.EntityFrameworkCore.Design
```

工程里出现 `PackageReference` 即成功。若想换 MySQL/PostgreSQL/SQL Server，只需换对应的 `...Database.xxx` 包，下面的代码几乎不变。

## 8.3 领域模型 → 表

```csharp
// Book.cs
public class Book
{
    public int Id { get; set; }                 // 主键（约定：名为 Id 或 XxxId）
    public string Title { get; set; } = "";
    public string Author { get; set; } = "";
    public decimal Price { get; set; }
    public DateTime PublishedAt { get; set; }

    public int? CategoryId { get; set; }        // 外键（可空 = 可选关联）
    public Category? Category { get; set; }     // 导航属性
}

public class Category
{
    public int Id { get; set; }
    public string Name { get; set; } = "";
    public List<Book> Books { get; set; } = new();   // 一对多：一个分类多本书
}
```

- 主键约定：名为 `Id` 或 `<类名>Id` 的整型自动当主键；
- 导航属性 `Category`/`Books` 表达关系，EF Core 据此建外键。

## 8.4 DbContext：通往数据库的“上下文”

```csharp
using Microsoft.EntityFrameworkCore;

public class BookStoreContext : DbContext
{
    // DbSet<T> = 对应数据库中的一张表
    public DbSet<Book> Books => Set<Book>();
    public DbSet<Category> Categories => Set<Category>();

    protected override void OnConfiguring(DbContextOptionsBuilder options)
    {
        options.UseSqlite("Data Source=bookstore.db");   // 连接串：单文件库
    }
}
```

## 8.5 迁移：从模型生成表结构

**迁移（Migration）** = 模型版本与数据库结构之间的“补丁脚本”，先打一次：

```bash
# 在工程目录执行
dotnet ef migrations add InitialCreate     # 根据模型生成一个迁移
dotnet ef database update                  # 把迁移应用到数据库（生成 bookstore.db）
```

之后每次改模型（加字段、加表），重复：`migrations add 名字` + `database update`。

> 若提示“找不到 dotnet-ef”，先安装全局工具：
> `dotnet tool install --global dotnet-ef`
> 并用 `dotnet ef --version` 验证。
> 也可以不用 CLI：开发期调用 `context.Database.EnsureCreated()`（仅适合学习/原型，不做版本管理）。

## 8.6 CRUD：增删改查

```csharp
// ============ 新增 ============
using (var db = new BookStoreContext())
{
    var tech = new Category { Name = "科技" };
    db.Categories.Add(tech);
    db.SaveChanges();                       // 真正写库

    db.Books.Add(new Book
    {
        Title = "C# 入门", Author = "小明",
        Price = 59.9m, PublishedAt = new DateTime(2026, 3, 1),
        CategoryId = tech.Id,
    });
    db.SaveChanges();
    Console.WriteLine($"新书 Id = {db.Books.First(b => b.Title == "C# 入门").Id}");
}
```

```csharp
// ============ 查询（LINQ 被翻译成 SQL） ============
using var db = new BookStoreContext();
// 全部
foreach (var b in db.Books) Console.WriteLine($"{b.Id} {b.Title} {b.Price}");

// 带条件的过滤：.ToList() 立即执行
var expensive = db.Books.Where(b => b.Price > 50m).OrderByDescending(b => b.Price).ToList();
Console.WriteLine($"50 元以上的书：{expensive.Count} 本");

// 取单个
Book? book = db.Books.FirstOrDefault(b => b.Title.Contains("C#"));
if (book != null) Console.WriteLine($"找到：{book.Title}");

// 投影（只取需要的列，少传数据）
var titles = db.Books.Select(b => new { b.Title, b.Author }).ToList();

// 聚合
Console.WriteLine($"平均价：{db.Books.Average(b => b.Price):C}");

// 连接查询（通过导航属性/Join）
var techBooks = db.Books.Where(b => b.Category!.Name == "科技").ToList();
```

```csharp
// ============ 修改 ============
using var db2 = new BookStoreContext();
Book? toUpdate = db2.Books.FirstOrDefault(b => b.Title == "C# 入门");
if (toUpdate != null)
{
    toUpdate.Price = 49.9m;              // 改属性
    db2.SaveChanges();                    // EF 跟踪到改动，生成 UPDATE
}
```

```csharp
// ============ 删除 ============
using var db3 = new BookStoreContext();
Book? toDelete = db3.Books.FirstOrDefault(b => b.Title == "C# 入门");
if (toDelete != null)
{
    db3.Books.Remove(toDelete);
    db3.SaveChanges();
}
```

## 8.7 异步数据库操作

EF Core 全系列支持异步（配合第六章），Web 场景必须用（否则占线程池线程）：

```csharp
Book? b = await db.Books.FirstOrDefaultAsync(x => x.Title.Contains("C#"));
var list = await db.Books.Where(x => x.Price > 30m).ToListAsync();
await db.SaveChangesAsync();
```

## 8.8 事务：要么全成，要么全不成

转账 = 两步写库。中间失败不能“扣了没加”。事务保证原子性：

```csharp
async Task TransferAsync(BookStoreContext db, int fromId, int toId, decimal amount)
{
    await using var tx = await db.Database.BeginTransactionAsync();   // 开启事务
    try
    {
        var from = await db.Books.FindAsync(fromId)
            ?? throw new InvalidOperationException("来源不存在");
        var to = await db.Books.FindAsync(toId)
            ?? throw new InvalidOperationException("目标不存在");

        if (from.Price < amount) throw new InvalidOperationException("余额不足");

        from.Price -= amount;                 // 这里语义是扣减字段值做演示
        to.Price += amount;

        await db.SaveChangesAsync();          // 一次提交所有改动
        await tx.CommitAsync();               // 全部成功 → 提交
    }
    catch
    {
        await tx.RollbackAsync();             // 任何异常 → 全部回滚
        throw;
    }
}
```

> 说明：示例为演示用“书的金额”做转账载体，字段语义可自行换成余额字段。事务三要点：BeginTransaction / Commit / Rollback（或 `SaveChanges` 失败自动回滚）。

## 8.9 查询性能与常见坑

| 坑 | 说明 | 正确姿势 |
| --- | --- | --- |
| 没加 `ToList` 就循环 | LINQ 延迟执行，连接没关 | 需要快照就 `.ToList()`/`ToListAsync` |
| N+1 查询 | 循环里逐条查导航属性 | 用 `Include`（预加载）或投影 |
| 全表拉取做内存过滤 | 大数据量 | 把 `Where` 写在 EF 查询里（在 SQL 端过滤） |
| 忘记异步 | Web 里阻塞线程 | 全部用 `*Async` |
| `FindAsync` 找不到就崩 | 返回 null | `??`/判空/`FirstOrDefaultAsync` |

预加载示例：

```csharp
// 每个分类下书籍数量（只查一次，避免 N+1）
var stats = await db.Categories
    .Include(c => c.Books)                       // 预加载 Books
    .Select(c => new { c.Name, Count = c.Books.Count })
    .ToListAsync();
foreach (var s in stats) Console.WriteLine($"{s.Name}: {s.Count}");
```

## 8.10 完整可运行示例：图书管理数据层

把 8.3~8.7 拼成一个可重复运行的“图书管理”控制台：

```csharp
using Microsoft.EntityFrameworkCore;

public class Book { public int Id { get; set; } public string Title { get; set; } = ""; public string Author { get; set; } = ""; public decimal Price { get; set; } public DateTime PublishedAt { get; set; } public int? CategoryId { get; set; } public Category? Category { get; set; } }
public class Category { public int Id { get; set; } public string Name { get; set; } = ""; public List<Book> Books { get; set; } = new(); }

public class StoreContext : DbContext
{
    public DbSet<Book> Books => Set<Book>();
    public DbSet<Category> Categories => Set<Category>();
    protected override void OnConfiguring(DbContextOptionsBuilder o)
        => o.UseSqlite("Data Source=bookstore.db");
}

// —— 顶级语句 ——
using (var db = new StoreContext())
{
    // 学习期简化建表（正式用迁移）
    db.Database.EnsureCreated();

    if (!db.Categories.Any())
    {
        var c1 = new Category { Name = "科技" };
        var c2 = new Category { Name = "文学" };
        db.Categories.AddRange(c1, c2);
        db.Books.AddRange(
            new Book { Title = "C# 从入门到放弃", Author = "A", Price = 59.9m, Category = c1, PublishedAt = DateTime.Now.AddYears(-1) },
            new Book { Title = "深入理解 LINQ", Author = "B", Price = 89m, Category = c1, PublishedAt = DateTime.Now.AddMonths(-3) },
            new Book { Title = "三体", Author = "刘慈欣", Price = 39.5m, Category = c2, PublishedAt = new DateTime(2008, 1, 1) });
        db.SaveChanges();
    }

    // 查：科技类书
    Console.WriteLine("—— 科技类书籍 ——");
    foreach (var b in db.Books.Where(b => b.Category!.Name == "科技").OrderBy(b => b.Price))
        Console.WriteLine($"  {b.Title} ￥{b.Price}");

    // 查：平均价 / 最贵的书
    Console.WriteLine($"平均书价：{db.Books.Average(b => b.Price):C}");
    var top = db.Books.OrderByDescending(b => b.Price).First();
    Console.WriteLine($"最贵：{top.Title} ￥{top.Price}");

    // 改：C# 书涨价 10%
    foreach (var b in db.Books.Where(b => b.Title.Contains("C#")))
    {
        b.Price *= 1.1m;
    }
    db.SaveChanges();
    Console.WriteLine("已为 C# 相关书籍涨价 10%");
}
```

重复运行：首次插入种子，后续直接查询（因为 `Any()` 判断过）。观察目录下出现 `bookstore.db`。

## 8.11 实战与验收

**必做**

1. 建一个 `Student` + `Class`（班级）一对多模型：学生（姓名/年龄/班级），班级有名称；
2. 用迁移建库，插入 2 个班级、5 个学生；
3. LINQ 查询：每班学生数、所有大于 18 岁的学生、按年龄排序前 3；
4. 实现“班级转学”：把某个学生从一个班转到另一个班（修改外键 + SaveChanges）。

**进阶题（任选）**

- 给 `Book` 加一个 `Tags` 多对多关系（`BookTag` 中间表，体验 EF 多对多配置）；
- 用事务模拟“图书馆借还”：还书时把 `IsBorrowed=false`，若借阅人黑名单则拒绝并回滚；
- 对比 Dapper：装 `Dapper` 包，手写一句 `SELECT` 查询同一张表，体会 ORM 与轻量 SQL 映射的分工。

**验收清单**

- [ ] 能说出 ORM 解决什么问题，EF Core 与 JPA/SQLAlchemy 的地位对等
- [ ] 会用 `dotnet ef migrations add` 与 `database update`
- [ ] 完成 DbSet + LINQ 的增删改查与异步版本
- [ ] 能写事务（BeginTransaction/Commit/Rollback）
- [ ] 知道 `Include` 预加载与延迟执行坑

## 8.12 小结

EF Core 把数据库操作“变成 LINQ”，这让第四章学的东西直接变现。你已具备：建模、建库、CRUD、事务、性能意识。下一章把它接入 **Web**：用 ASP.NET Core 把数据库能力以 REST 服务的形式暴露给前端。

## 语言对照

| 概念 | C# | Java | Python | Rust |
| --- | --- | --- | --- | --- |
| ORM | EF Core | JPA/Hibernate、MyBatis | SQLAlchemy | SeaORM/sqlx |
| 迁移 | `dotnet ef migrations` | Flyway/Liquibase | Alembic | sqlx migrate |
| 模型类 | POCO + DbContext | Entity + Repository | Model + Base | struct + derive |
| 查询 | LINQ → SQL | JPQL/Criteria | Query/表达式 | query builder |
| 连接串 | `Data Source=x.db` | JDBC URL | `sqlite:///x.db` | `sqlite:x.db` |
| 常用库 | SQLite（本教程） | MySQL/PostgreSQL | SQLite/PostgreSQL | SQLite/Postgres |

下一章：[09-Web开发ASP.NETCore.md](./09-Web开发ASP.NETCore.md) ｜ [返回 README](./README.md)
