# Java 学习与实战项目仓库

本仓库包含两部分内容：**体系化学习文档**与**配套实战项目**。

## 目录结构

```
docs/
├── README.md                  # 本文件：仓库总览
├── java-learning/             # 39 章体系化学习文档（基础 -> Web -> 微服务 -> 深度专题 -> 专项精讲 -> 实战）
├── python-learning/           # Python 体系化学习文档（基础 -> 进阶 -> 并发 -> 网络/Web -> 数据分析，与 Java 体系并列）
├── mall-boot/                 # Spring Boot 单体商城项目（可运行，配套第 31 章）
├── mall-cloud/                # Spring Cloud 微服务商城项目（配套第 30、32 章）
└── migration-demo/            # 数据迁移实操项目（Spring Boot + Flyway + MySQL，配套第 38 章）
```

## 一、学习文档

### 1. Java 体系（java-learning/）

39 章完整学习路线，覆盖：Java 基础、面向对象、集合、多线程、IO/网络、
Spring Boot、缓存、安全、设计模式、微服务、消息队列、容器化、搜索、
CI/CD、JVM 内功、MySQL 调优、Netty、Kafka、Redis 深度、微服务治理、
Spring Boot/Cloud 项目实战，以及 MySQL/Redis/Docker/Kong/Nacos/数据迁移/建表脚本 七大专项精讲等。
详见 [`java-learning/README.md`](./java-learning/README.md)。

### 2. Python 体系（python-learning/）

面向已有 Java 基础的读者，全程穿插 Python vs Java 对比，**9 章体系 + 7 章 AI/数据扩展已全部完成**：
基础与核心语法、进阶语法（迭代器/装饰器/元类/类型注解）、文件与标准库、
并发与异步（GIL/asyncio）、网络爬虫、FastAPI Web 开发、数据分析（NumPy/Pandas/Matplotlib）、
自动化脚本与工程化、工程化与测试（pytest/打包/CI/CD）、
AI Agent 全栈（OpenAI SDK Agent 开发、RAG 与向量数据库、LangGraph 编排、MCP 工具接入、
机器学习 scikit-learn、多模态语音 Agent、Agent 评测体系）。
详见 [`python-learning/README.md`](./python-learning/README.md)。

## 二、实战项目

| 项目 | 说明 | 配套章节 | 快速入口 |
| --- | --- | --- | --- |
| **mall-boot** | Spring Boot 3.2 单体商城：JWT 登录、缓存治理、防超卖下单、Redis 对接验证、SkyWalking 探针、Docker/K8s/Helm 部署 | 第 31 章 | [mall-boot/README.md](./mall-boot/README.md) |
| **mall-cloud** | Spring Cloud 微服务商城：Nacos + Gateway + Feign + Seata 分布式事务 + Sentinel 限流（规则配置中心化）+ SkyWalking + K8s/Helm 部署 | 第 30、32 章 | [mall-cloud/README.md](./mall-cloud/README.md) |
| **migration-demo** | 数据迁移实操：Spring Boot + Flyway 自动执行 V1~V5 版本化迁移脚本（建表/加字段/回填数据/索引），含 docker-compose 一键拉起 MySQL | 第 38 章 | [migration-demo/README.md](./migration-demo/README.md) |

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
