# C# 学习指南（从零基础到工程实践）

> 本文档与 [java-learning](../java-learning/README.md)、[python-learning](../python-learning/README.md)、[rust-learning](../rust-learning/README.md) 并列，是仓库的 **C# / .NET 体系化学习资料**。
>
> 这套文档**从零基础起步**：不预设任何编程经验，从安装 .NET SDK、写第一行代码讲起；同时每章附「与 Java/Python/Rust 的对照」小节，让已有其它语言基础的读者也能快速迁移。
>
> ✅ **12 章体系已发布：主线 9 章 + 高级专题 3 章**。目标是一条线掌握：C# 语法 → 面向对象 → LINQ → 异步并发 → .NET 平台 → EF Core → ASP.NET Core → 测试与发布 → 进阶底层与生态，最终能独立交付可运行的 Web 服务与工具。
>
> 🎮 **游戏方向延伸**：若想用 C# 做游戏，见 [unity-learning/](./unity-learning/README.md) —— Unity 引擎游戏开发专题（**主线 4 章 + 进阶 3 章**：uGUI/TMP 界面、Animator 动画、2D 精灵开发 + 纯代码 demo-project 演示工程）。

## 为什么学 C#

- **全场景覆盖**：Web（ASP.NET Core）、桌面（WinForms/WPF/MAUI）、游戏（Unity）、云原生（Azure）、AI（ML.NET / Semantic Kernel）、大数据（.NET for Apache Spark），一门语言 + 一个运行时通吃；
- **工程化成熟**：类型安全、`nullable` 引用类型、LINQ、async/await 一等公民，编译期帮你挡掉大量低级错误；
- **托管语言**：有 GC 自动内存管理，语法表达力强，对零基础学习者比 C/C++ 友好得多；
- 与 Java 生态高度相似（类、接口、JIT + 字节码模型），是 Java 工程师扩展技能树的高性价比选择。

## 目录结构

| 章节 | 内容 | 文件 | 状态 |
| --- | --- | --- | --- |
| 第一章 | 环境搭建与 C# 基础语法：.NET SDK 安装、dotnet CLI、控制台程序结构、变量与基本类型、运算符、流程控制、方法、数组与字符串入门、读懂编译器报错 | [01-CSharp环境与基础语法.md](./01-CSharp环境与基础语法.md) | ✅ 已发布 |
| 第二章 | 面向对象编程：类与对象、属性与封装、构造器、static、继承与多态、抽象类与接口、record、与 Java/Python 对照 | [02-面向对象编程.md](./02-面向对象编程.md) | ✅ 已发布 |
| 第三章 | 类型系统进阶与模式匹配：struct/枚举/元组、可空值类型、可空引用类型、switch 表达式、模式匹配全家桶、C# 版本特性地图 | [03-类型系统进阶与模式匹配.md](./03-类型系统进阶与模式匹配.md) | ✅ 已发布 |
| 第四章 | 集合、委托与 LINQ：List/Dictionary/HashSet、委托与 lambda、Func/Action、扩展方法、LINQ 查询语法与方法语法 | [04-集合委托与LINQ.md](./04-集合委托与LINQ.md) | ✅ 已发布 |
| 第五章 | 错误处理与调试：异常体系、try/catch/finally/throw、自定义异常、防御式编程、调试器（断点/单步/监视）、日志初识 | [05-错误处理与调试.md](./05-错误处理与调试.md) | ✅ 已发布 |
| 第六章 | 异步与并发：async/await 与 Task、CPU 密集任务、线程与 lock、并发集合、超时与取消、常见异步陷阱 | [06-异步与并发.md](./06-异步与并发.md) | ✅ 已发布 |
| 第七章 | .NET 平台与常用类库：NuGet 包管理、System.IO 文件读写、System.Text.Json、HttpClient、日期时间、正则表达式、应用配置 | [07-.NET平台与常用类库.md](./07-.NET平台与常用类库.md) | ✅ 已发布 |
| 第八章 | 数据库与 EF Core：关系库与 ORM、SQLite、DbContext 与模型映射、关系、迁移、LINQ 增删改查、事务 | [08-EFCore数据持久化.md](./08-EFCore数据持久化.md) | ✅ 已发布 |
| 第九章 | Web 开发 ASP.NET Core：HTTP 与 REST、Minimal API、依赖注入、DTO 与验证、集成 EF Core、Swagger、发布 | [09-Web开发ASP.NETCore.md](./09-Web开发ASP.NETCore.md) | ✅ 已发布 |
| 第十章 | 测试、质量与发布：xUnit、Fact/Theory、Moq、集成测试、覆盖率、静态分析、GitHub Actions、Docker、NuGet 打包 | [10-测试质量与发布.md](./10-测试质量与发布.md) | ✅ 已发布 |
| 第十一章 | 深入 CLR 与底层：值/引用内存模型、GC、IDisposable/using、Span/Memory、unsafe 指针、P/Invoke 本地互操作 | [11-深入CLR-Span与本地互操作.md](./11-深入CLR-Span与本地互操作.md) | ✅ 已发布 |
| 第十二章 | .NET 生态与学习收官：桌面/游戏/云/AI/大数据生态巡礼、Source Generators、多语言对照总表、资源与路线图 | [12-.NET生态与收官.md](./12-.NET生态与收官.md) | ✅ 已发布 |

## 学习路线

```text
安装 .NET SDK（第一章）
      │
基础语法（变量/类型/流程/方法/数组/字符串）
      │
面向对象编程（类/接口/继承/多态/record）
      │
类型系统进阶 + 模式匹配
      │
集合 / 委托 / LINQ ──────────────► 数据库：EF Core
      │                                   │
错误处理与调试                          ASP.NET Core Web（REST 服务）
      │                                   │
异步与并发 ◄─────────────────────────────┘ （Web 天然异步）
      │
.NET 平台常用类库（文件/JSON/网络/配置）
      │
测试、质量与发布（工程化主线收官）
      │
高级专题：CLR 底层 / Span / unsafe / FFI ──► .NET 生态与总收官
```

- **主线 1~9 章**：从“写第一行代码”到“交付一个带数据库的 REST 服务 + 基本质量保障”；
- **高级 10~12 章**：把测试体系补全、向下钻到内存与互操作层、向上看生态全景。

## 阶段产出（每章实战）

| 阶段 | 核心内容 | 章节实战产出 |
| --- | --- | --- |
| 1 | 环境、变量、类型、流程、方法 | 控制台计算器（不崩溃版） |
| 2 | 类、属性、继承、接口、record | 银行账户体系 |
| 3 | 枚举、可空、模式匹配 | 支付方式判断器 |
| 4 | 集合、lambda、LINQ | 学生成绩管理系统 |
| 5 | 异常、调试、日志 | 健壮版待办记事本 |
| 6 | async/await、线程、锁 | 并发下载/并行求值器 |
| 7 | 文件、JSON、网络 | 天气 JSON 拉取 + 本地缓存 |
| 8 | EF Core | 图书管理数据层（CRUD + 事务） |
| 9 | ASP.NET Core | 待办 REST API（Swagger + EF） |
| 10 | 测试/CI/Docker | 带 CI 流水线的测试套件 + 镜像 |
| 11 | 内存/互操作 | 高性能字符串处理 + P/Invoke |
| 12 | 生态收官 | 你的下一步方向自选 Demo |

## 与 Java / Python / Rust 速查对照

| C# | Java | Python | 备注 |
| --- | --- | --- | --- |
| `class` / `interface` | `class` / `interface` | class / protocol | 面向对象主形态几乎同构 |
| 属性（Property） | 字段 + getter/setter | `@property` | C# 有语法级属性 |
| `record` | Java 16 `record` | dataclass | 值相等 + 不可变 |
| `enum` | `enum` | `Enum` | C# 枚举是值类型 |
| `List<T>` | `List<T>`/`ArrayList` | `list` | |
| `Dictionary<K,V>` | `HashMap<K,V>` | `dict` | |
| LINQ | `Stream` | 推导式/列表表达式 | C# 有查询语法 `from…where…` |
| `async/await`/`Task` | `CompletableFuture`/虚拟线程 | `asyncio` | C# 语法级一等公民 |
| 属性/Attribute | 注解/Annotation | 装饰器 | C# Attribute 偏声明式配置 |
| NuGet | Maven/Gradle | pip/uv | 包管理 |
| `dotnet CLI` | Maven | uv/poetry | 全流程官方 CLI |
| ASP.NET Core | Spring Boot | FastAPI | 三套 Web 框架同章节对照 |
| EF Core | MyBatis/JPA | SQLAlchemy | ORM |
| 异常 + try/catch | 异常 + try/catch | 异常 | 三语言几乎一致 |
| GC 托管内存 | JVM GC | 引用计数 | C# 还有 `Span<T>`/`unsafe` |

## 环境要求

- **.NET SDK**（长期支持版，推荐 10.x LTS）：https://dotnet.microsoft.com/download —— 装好后在终端执行 `dotnet --version` 验证；
- 编辑器（任选其一）：Windows 推荐 Visual Studio 2022 Community（免费）；跨平台推荐 VS Code + C# Dev Kit 插件；
- SQLite 无需额外安装（EF Core 的 SQLite provider 内嵌于 NuGet 包）；
- **写作与校验说明**：本文档编写时本机暂未安装 .NET SDK，全部代码**未实机编译**。请按第一章安装 SDK 后逐章运行验证；若你本机 SDK 模板有小差异（例如 `dotnet new console` 生成的文件注释不同），以模板实际生成为准，代码逻辑不受影响。

## 学习建议

1. **零基础友好节奏**：每章先跟着“动手做”把代码跑起来，再读概念解释，最后独立完成章末“实战与验收”；
2. **把编译器当老师**：C# 报错通常带 `CS` 编号和精确行号，先自己读 3 分钟再查资料；
3. **每章都用真实项目练手**：不要只看代码块，把每章的“完整可运行示例”敲进自己的 `Program.cs`；
4. **有其它语言基础的同学**：可跳过已熟悉部分，重点看每章“语言对照”小节与踩坑提醒；
5. **查文档首选官方**：https://learn.microsoft.com/dotnet/csharp 与 https://learn.microsoft.com/dotnet/api

下一章：[01-CSharp环境与基础语法.md](./01-CSharp环境与基础语法.md)
