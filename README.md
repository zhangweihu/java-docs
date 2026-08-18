# Java 学习与实战项目仓库

本仓库包含两部分内容：**体系化学习文档**与**配套实战项目**。

## 目录结构

```
docs/
├── README.md                  # 本文件：仓库总览
├── java-learning/             # 35 章体系化学习文档（基础 -> Web -> 微服务 -> 深度专题 -> 专项精讲 -> 实战）
├── mall-boot/                 # Spring Boot 单体商城项目（可运行，配套第 31 章）
└── mall-cloud/                # Spring Cloud 微服务商城项目（配套第 30、32 章）
```

## 一、学习文档（java-learning/）

35 章完整学习路线，覆盖：Java 基础、面向对象、集合、多线程、IO/网络、
Spring Boot、缓存、安全、设计模式、微服务、消息队列、容器化、搜索、
CI/CD、JVM 内功、MySQL 调优、Netty、Kafka、Redis 深度、微服务治理、
Spring Boot/Cloud 项目实战，以及 MySQL/Redis/Docker 三大专项精讲等。
详见 [`java-learning/README.md`](./java-learning/README.md)。

## 二、实战项目

| 项目 | 说明 | 配套章节 | 快速入口 |
| --- | --- | --- | --- |
| **mall-boot** | Spring Boot 3.2 单体商城：JWT 登录、缓存治理、防超卖下单、Redis 对接验证、SkyWalking 探针、Docker/K8s/Helm 部署 | 第 31 章 | [mall-boot/README.md](./mall-boot/README.md) |
| **mall-cloud** | Spring Cloud 微服务商城：Nacos + Gateway + Feign + Seata 分布式事务 + Sentinel 限流（规则配置中心化）+ SkyWalking + K8s/Helm 部署 | 第 30、32 章 | [mall-cloud/README.md](./mall-cloud/README.md) |

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
