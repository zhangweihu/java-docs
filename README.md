# Java 学习与实战项目仓库

本仓库包含两部分内容：**体系化学习文档**与**配套实战项目**。

## 目录结构

```
docs/
├── README.md                  # 本文件：仓库总览
├── java-learning/             # 85 章体系化学习文档（基础 -> Web -> 微服务 -> 深度专题 -> 专项精讲 -> 实战 -> 领域建模 -> DDD/状态机/CQRS -> 分布式事务 -> 企业级架构 -> 云原生 -> 行业方案 -> 工程化与 Operator -> 数据专题 -> 大数据专题 -> DDD×报表工具 -> DDD×分层架构专题 -> 工具库扩展专题 openCSV/Tika -> Harness 专题 测试 Harness + AI Agent Harness 收官 -> Code Review Agent 实战收官）
├── python-learning/           # Python 体系化学习文档（基础 -> 进阶 -> 并发 -> 网络/Web -> 数据分析，与 Java 体系并列）
├── rust-learning/             # Rust 体系化学习文档（README 索引 + 13 章：主线 9 章（基础->所有权->类型->集合/错误->智能指针->并发->异步->Axum Web->工程化）+ 高级专题 4 章（过程宏->unsafe/FFI->Polars 数据分析->大数据/爬虫），每章含实战）+ examples 配套可运行工程
├── csharp-learning/           # C# 体系化学习文档（README 索引 + 12 章：主线 9 章（环境->基础语法->面向对象->类型/模式匹配->集合/LINQ->错误调试->异步并发->.NET 类库->EF Core->ASP.NET Core Web）+ 高级专题 3 章（测试/质量/CI-Docker->CLR 底层/Span/unsafe/FFI->生态收官），零基础起步风格，每章含代码与实战）
│   └── unity-learning/        # C# 游戏方向延伸专题（Unity 引擎：README + 7 章：主线 4 章（引擎与编辑器基础->脚本/生命周期/组件->物理/输入/UI->完整小游戏实战与发布）+ 进阶 3 章（uGUI/TMP 界面->动画 Animator->2D 精灵开发）+ demo-project 纯代码最小演示工程）
├── opengl-learning/           # OpenGL/C++ 图形学学习文档（README 索引 + 8 章：主线 5 章（渲染管线与环境->三角形与着色器->纹理->变换与坐标系统->摄像机自由漫游）+ 进阶 3 章（Phong 光照与材质->高级渲染特性->帧缓冲后处理与天空盒），章节内完整代码，无配套工程）
├── mall-boot/                 # Spring Boot 单体商城项目（可运行，配套第 31 章）
├── mall-cloud/                # Spring Cloud 微服务商城项目（配套第 30、32 章）
├── migration-demo/            # 数据迁移实操项目（Spring Boot + Flyway + MySQL，配套第 38 章）
└── ddd-learning/              # DDD × 分层架构实战专题（配套第 71~80 章；含 5 个可运行示例工程 examples/01-04 + spring-ai-code-review-agent）
```

## 一、学习文档

### 1. Java 体系（java-learning/）

85 章完整学习路线，覆盖：Java 基础、面向对象、集合、多线程、IO/网络、
Spring Boot、缓存、安全、设计模式、微服务、消息队列、容器化、搜索、
CI/CD、JVM 内功、MySQL 调优、Netty、Kafka、Redis 深度、微服务治理、
Spring Boot/Cloud 项目实战，以及 MySQL/Redis/Docker/Kong/Nacos/数据迁移/建表脚本 七大专项精讲、
贫血模型与充血模型、DDD 战术设计（聚合/值对象/仓储）、Spring StateMachine 状态机、
CQRS 与事件溯源、事件风暴工作坊、Axon Framework、分布式事务与 Saga、
高并发/高可用/可观测性/稳定性保障的企业级架构专题，
以及 K8s 深度、Service Mesh/Istio、Serverless 云原生三连，
DevOps/GitOps、云原生安全、更多行业案例（教育/医疗/政务/制造/旅游/SaaS）、K8s Operator 开发，
数据专题四连（分库分表与 ShardingSphere 深度实战、数据库高可用与容灾、
大规模数据迁移与不停机扩容、分布式数据库与 NewSQL），
大数据专题九连：Flink 流式计算、Flink 实时数仓分层实战、Spark 体系、
ClickHouse/Doris 分析引擎、大数据平台全景、Hive 深度实战、数据湖 Iceberg 实战、
机器学习平台、BI 可视化实践，
DDD×企业报表工具实战（DataMax 集成实战），
DDD × 分层架构专题（第 71~80 章）：分层架构全景、整洁架构落地、六边形架构、
应用服务与 CQRS 入口、Modular Monolith 落地、订单/库存/支付三大领域实战、跨上下文 Saga/Outbox 集成、
演进路线与 30 道面试冲刺题；并在 [`ddd-learning/examples/`](./ddd-learning/examples/) 提供 4 个可运行示例工程
（01 分层架构对比 / 02 模块化单体 / 03 订单完整 DDD / 04 Saga 跨服务集成），
**最终以工具库扩展专题收官（第 81~82 章）**：openCSV 实战（CSV 读写 / 注解映射 / 流式处理 / 用户导入订单导出）、
Apache Tika 实战（1400+ 文件格式检测 / 元数据抽取 / 文本提取 / Spring Boot 上传安全与全文检索集成），
并以 Harness 专题收官（第 83~84 章）：**软件测试 Harness**（JUnit 5 / Mockito / Spring Boot Test / Testcontainers / REST Assured / JaCoCo）、
**AI Agent Harness**（Spring AI / Function Calling / ReAct / Memory / Plan-and-Execute / 多 Agent 编排 / 客服 Agent 实战），
**并以企业级 DevOps 落地收官（第 85 章）**：Spring AI 搭建内部 Code Review Agent
（多 Agent 并行协作：安全/性能/DDD/测试/风格 五维审查 + 多 LLM 切换 + GitLab/GitHub 双平台接入 +
Webhook + 飞书/钉钉通知 + 严重问题自动阻塞合并），配套 [`ddd-learning/examples/spring-ai-code-review-agent/`](./ddd-learning/examples/spring-ai-code-review-agent/) 完整可运行工程。
详见 [`java-learning/README.md`](./java-learning/README.md)。

### 2. Python 体系（python-learning/）

面向已有 Java 基础的读者，全程穿插 Python vs Java 对比，**9 章体系 + 7 章 AI/数据扩展已全部完成**：
基础与核心语法、进阶语法（迭代器/装饰器/元类/类型注解）、文件与标准库、
并发与异步（GIL/asyncio）、网络爬虫、FastAPI Web 开发、数据分析（NumPy/Pandas/Matplotlib）、
自动化脚本与工程化、工程化与测试（pytest/打包/CI/CD）、
AI Agent 全栈（OpenAI SDK Agent 开发、RAG 与向量数据库、LangGraph 编排、MCP 工具接入、
机器学习 scikit-learn、多模态语音 Agent、Agent 评测体系）。
详见 [`python-learning/README.md`](./python-learning/README.md)。

### 3. Rust 体系（rust-learning/）

面向已有编程基础的读者，**README 索引 + 13 章体系化学习文档 + 每章实战 + examples 配套可运行工程已全部完成**：
主线 9 章——Rust 环境搭建与基础语法、所有权/借用/生命周期、结构体与枚举与 trait 与泛型、
集合与迭代器与闭包与模块、错误处理（thiserror/anyhow）、智能指针与内存管理、并发（线程/mpsc/Arc）、
Tokio 异步、Axum Web + SQLx 数据库、测试与工程化发布（clippy/Docker/CI）；
高级专题 4 章——过程宏与元编程（syn/quote 自定义派生宏）、unsafe 与 FFI（extern "C"/cbindgen/Miri）、
Polars 数据分析（Arrow/Parquet/惰性框架）、大数据与爬虫实战（reqwest/scraper + DataFusion SQL 管道）。
一章一实战（命令行计算器 → 领域模型 → 词频统计 → 并发调度器 → 异步抓取器 → REST 服务 → 可交付待办服务 →
派生宏 → 环形缓冲 → 数据分析 → 采集分析管道），工程代码见 [`rust-learning/examples/`](./rust-learning/examples/)。
详见 [`rust-learning/README.md`](./rust-learning/README.md)。

### 4. C# 体系（csharp-learning/）

**零基础友好定位**（不预设编程经验，从安装 .NET SDK 讲起），同时每章附「与 Java/Python/Rust 对照」便于迁移，**README 索引 + 12 章体系化文档已全部完成**：
主线 9 章——环境与 C# 基础语法、面向对象编程、类型系统进阶与模式匹配、集合/委托/LINQ、
错误处理与调试、异步与并发（async/await/Task）、.NET 平台常用类库（文件/JSON/HttpClient/正则/NuGet）、
数据库与 EF Core（迁移/CRUD/事务）、Web 开发 ASP.NET Core（Minimal API/REST/Swagger/发布）；
高级专题 3 章——测试质量与发布（xUnit/集成测试/Docker/CI）、深入 CLR 与底层（值/引用内存、GC、
Span/unsafe/指针/P-Invoke 本地互操作）、.NET 生态与收官（桌面/游戏/云/AI/大数据巡礼 + 多语言对照总表）。
一章一实战（控制台计算器 → 银行账户体系 → 学生成绩管理 → 待办记事本 → 天气缓存工具 → 图书管理 → 待办 REST API → CI 测试套件 → 高性能与互操作示例）。
详见 [`csharp-learning/README.md`](./csharp-learning/README.md)。

### 5. Unity 游戏开发专题（csharp-learning/unity-learning/）

**C# 体系的游戏方向延伸**：面向会用 C# 写控制台/Web 程序、但对游戏引擎零基础的读者，**README 索引 + 7 章文档（主线 4 章 + 进阶 3 章）+ 纯代码演示工程已全部完成**：
主线：第一章 Unity 引擎与编辑器基础（场景/GameObject/组件/Transform/Prefab）→ 第二章 C# 脚本与生命周期（MonoBehaviour/Awake/Start/Update/Time.deltaTime/组件通信/协程）→
第三章 物理碰撞、输入与 UI（Rigidbody/Collider/碰撞与触发事件/旧与新输入系统/uGUI 与 IMGUI）→ 第四章 完整小游戏实战与发布（需求拆解/状态机/代码驱动场景/Build 发布）。
进阶：第五章 uGUI 与 TextMeshPro 界面开发（Canvas/锚点布局/事件系统/TMP 中文与富文本）→ 第六章 动画系统 Animator 状态机（AnimationClip/参数驱动/动画事件/Any State）→ 第七章 2D 精灵与 2D 游戏开发（正交相机/SpriteRenderer/2D 物理/代码生成精灵）。
一章一实战（编辑器搭场景 → 生命周期观察器/变速小球 → 迷你打靶场 → 「接宝石」完整小游戏 → TMP HUD → 三态动画机 → 2D 彩球沙盘）。
配套 [`demo-project/`](./csharp-learning/unity-learning/demo-project/) 为**零美术资源、纯 C# 自举**的最小 Unity 工程：无第三方包与场景文件，打开即跑五个演示（按 1~5 切换；进阶中的 uGUI/Animator 因依赖包与编辑器资产，改为章节内手把手实操）。
详见 [`csharp-learning/unity-learning/README.md`](./csharp-learning/unity-learning/README.md)。

### 6. OpenGL 图形学专题（opengl-learning/）

**图形 / GPU 渲染方向**（宿主语言 C++，glad + GLFW 路线）：面向已有任一语言编程经验、想理解“引擎黑盒之下”的读者，**README 索引 + 8 章文档（主线 5 章 + 进阶 3 章）已全部完成**：
主线：第一章 渲染管线总览与环境（GLFW/glad/GLM、最小窗口）→ 第二章 绘制三角形与着色器（VBO/VAO/EBO、GLSL）→ 第三章 纹理与采样（程序化棋盘/图片加载/多纹理）→ 第四章 变换与坐标系统（GLM/MVP/深度测试/3D 立方体）→ 第五章 摄像机与自由漫游（lookAt/欧拉角/deltaTime/Camera 类）。
进阶：第六章 Phong 光照模型与材质（环境/漫反射/高光、法线矩阵）→ 第七章 高级渲染特性（深度细节/面剔除/混合/模板描边/多重采样）→ 第八章 帧缓冲后处理与天空盒（离屏渲染/滤镜/天空盒反射）。
一章一实战（清屏换色 → 彩色三角形/矩形 → 棋盘纹理矩形 → 自转 3D 立方体 → WASD+鼠标自由漫游 → 受光立方体 → 描边+半透明场景 → 反色/像素化后处理）。**只提供学习文档**，示例为章节内可复制的完整代码 + 第一章一次性工程模板，不附带可编译工程（写作时未实机编译，请在本地按章验证）。
详见 [`opengl-learning/README.md`](./opengl-learning/README.md)。

## 二、实战项目

| 项目 | 说明 | 配套章节 | 快速入口 |
| --- | --- | --- | --- |
| **mall-boot** | Spring Boot 3.2 单体商城：JWT 登录、缓存治理、防超卖下单、Redis 对接验证、SkyWalking 探针、Docker/K8s/Helm 部署 | 第 31 章 | [mall-boot/README.md](./mall-boot/README.md) |
| **mall-cloud** | Spring Cloud 微服务商城：Nacos + Gateway + Feign + Seata 分布式事务 + Sentinel 限流（规则配置中心化）+ SkyWalking + K8s/Helm 部署 | 第 30、32 章 | [mall-cloud/README.md](./mall-cloud/README.md) |
| **migration-demo** | 数据迁移实操：Spring Boot + Flyway 自动执行 V1~V5 版本化迁移脚本（建表/加字段/回填数据/索引），含 docker-compose 一键拉起 MySQL | 第 38 章 | [migration-demo/README.md](./migration-demo/README.md) |
| **ddd-learning** | DDD × 分层架构实战：含 5 个 Spring Boot 3.2 示例工程（examples/01 分层架构对比 / 02 模块化单体 / 03 订单完整 DDD / 04 Saga 跨服务集成 / spring-ai-code-review-agent 多 Agent 自动化 Code Review），演示 MVC/三层/整洁架构/六边形/Modular Monolith 对比、ArchUnit 边界守护、Saga+Outbox 跨服务一致、Spring AI Function Calling + 多 Agent 并行审查 | 第 71~80、85 章 | [ddd-learning/README.md](./ddd-learning/README.md) |

### 两个项目的关系（学习路径）

```
            mall-boot（单体，先跑起来）
                  │  演进：什么时候该拆微服务？（第 32 章 32.1）
                  ▼
            mall-cloud（微服务，治理全家桶）
```

- **mall-boot** 是单体版本：一个进程搞定用户/商品/订单/购物车，适合理解业务闭环；
- **mall-cloud** 是演进版本：按领域拆分为用户/商品/订单三个服务 + 网关统一入口，
  服务间用 Feign 契约调用，跨库事务交给 Seata，注册/配置交给 Nacos。

建议学习顺序：`学习文档第 31 章 -> 跑通 mall-boot -> 学习文档第 30/32 章 -> 跑通 mall-cloud`。

## 三、环境要求

- JDK 17+、Maven 3.8+
- MySQL 8.0、Redis 7.x（也可直接用各项目 docker/ 目录一键拉起）
- mall-cloud 额外需要 Docker（Nacos、Seata、Sentinel 控制台）
- （可选）SkyWalking OAP/UI（探针接入）、Kubernetes 集群（原生清单见各项目 `k8s/`，一键部署见各项目 `helm/`）
