# Java 学习指南（从入门到实战）

> 本文档是一份面向初学者的 Java 详细学习资料，每一章都配有**完整可运行**的代码示例。
> 建议学习方式：按章节顺序阅读，**亲手把每个示例敲一遍并运行**，再完成章节末尾的练习。

## 目录结构

| 章节 | 内容 | 文件 |
| --- | --- | --- |
| 第一章 | 环境搭建、Hello World、基础语法（变量/数据类型/运算符/流程控制/数组） | [01-基础语法.md](./01-基础语法.md) |
| 第二章 | 面向对象：类与对象、封装、继承、多态、抽象类、接口 | [02-面向对象编程.md](./02-面向对象编程.md) |
| 第三章 | 常用 API、字符串、集合框架（List/Set/Map）、日期时间 | [03-常用API与集合框架.md](./03-常用API与集合框架.md) |
| 第四章 | 异常处理、泛型、Lambda 与 Stream | [04-异常泛型与Lambda.md](./04-异常泛型与Lambda.md) |
| 第五章 | 多线程与并发（Thread、Runnable、锁、线程池） | [05-多线程与并发.md](./05-多线程与并发.md) |
| 第六章 | IO 流、文件操作、网络编程、JDBC 数据库 | [06-IO网络与JDBC.md](./06-IO网络与JDBC.md) |
| 第七章 | 综合实战：图书管理系统 + 学生成绩管理系统 | [07-综合实战.md](./07-综合实战.md) |
| 第八章 | 发送邮件：JavaMail、文本/HTML/附件/图片邮件、异步与批量发送 | [08-发送邮件.md](./08-发送邮件.md) |
| 第九章 | Spring Boot：RESTful API、MyBatis、统一异常处理、邮件整合、打包部署 | [09-SpringBoot.md](./09-SpringBoot.md) |
| 第十章 | Lombok 与 MapStruct：编译期注解处理、减少样板代码、DTO 映射 | [10-Lombok与MapStruct.md](./10-Lombok与MapStruct.md) |
| 第十一章 | 日志框架：SLF4J 门面、Logback、Log4j2、异步日志、MDC 追踪 | [11-日志框架.md](./11-日志框架.md) |
| 第十二章 | Redis 缓存：五种数据类型、Spring Boot 整合、穿透/击穿/雪崩、分布式锁 | [12-Redis.md](./12-Redis.md) |
| 第十三章 | Spring Security：认证授权、BCrypt、RBAC、JWT 无状态登录 | [13-SpringSecurity.md](./13-SpringSecurity.md) |
| 第十四章 | 设计模式：六大原则 + 12 种常用模式（单例/工厂/代理/策略等） | [14-设计模式.md](./14-设计模式.md) |
| 第十五章 | Spring Cloud 微服务：Nacos、OpenFeign、Gateway、Sentinel、配置中心 | [15-SpringCloud.md](./15-SpringCloud.md) |
| 第十六章 | RabbitMQ 消息队列：五种模式、延迟队列、死信、可靠性投递 | [16-RabbitMQ.md](./16-RabbitMQ.md) |
| 第十七章 | MyBatis-Plus：免 SQL CRUD、条件构造器、分页、逻辑删除、乐观锁 | [17-MyBatisPlus.md](./17-MyBatisPlus.md) |
| 第十八章 | XXL-Job 分布式任务调度：调度中心、执行器、CRON、分片广播 | [18-XXLJob.md](./18-XXLJob.md) |
| 第十九章 | Docker 与 K8s：镜像、容器、Compose、Deployment、Service、Ingress | [19-Docker与K8s.md](./19-Docker与K8s.md) |
| 第二十章 | Elasticsearch：倒排索引、DSL 查询、IK 分词、Spring Data ES、ELK | [20-Elasticsearch.md](./20-Elasticsearch.md) |
| 第二十一章 | Jenkins CI/CD：Pipeline、Webhook 自动构建、Docker 部署、回滚 | [21-JenkinsCI-CD.md](./21-JenkinsCI-CD.md) |
| 第二十二章 | Nginx：反向代理、负载均衡、动静分离、HTTPS、限流高可用 | [22-Nginx.md](./22-Nginx.md) |
| 第二十三章 | Zookeeper：数据模型、ZAB 协议、选举、分布式锁、注册中心 | [23-Zookeeper.md](./23-Zookeeper.md) |
| 第二十四章 | JVM 调优：内存模型、GC、常用参数、jstat/jmap/jstack、OOM 排查 | [24-JVM调优.md](./24-JVM调优.md) |
| 第二十五章 | MySQL 调优：B+Tree 索引、EXPLAIN、SQL 优化、事务锁、主从分库 | [25-MySQL调优.md](./25-MySQL调优.md) |
| 第二十六章 | Netty：NIO 多路复用、EventLoop、Pipeline、粘包拆包、心跳 | [26-Netty.md](./26-Netty.md) |
| 第二十七章 | Kafka：分区副本、消费者组、可靠性投递、消息堆积、顺序性 | [27-Kafka.md](./27-Kafka.md) |
| 第二十八章 | 设计原则深度：SOLID 六大原则、组合优于继承、坏味道与重构 | [28-设计原则深度.md](./28-设计原则深度.md) |
| 第二十九章 | Redis 深度篇：底层数据结构、持久化、内存淘汰、主从哨兵集群、分布式锁深挖 | [29-Redis深度篇.md](./29-Redis深度篇.md) |
| 第三十章 | 微服务治理实战：注册发现、配置中心、网关治理、熔断限流、分布式事务、可观测性 | [30-微服务治理实战.md](./30-微服务治理实战.md) |
| 第三十一章 | Spring Boot 项目实战：商城系统从 0 到 1（分层规范、防超卖、缓存、安全、Docker 部署） | [31-SpringBoot项目实战.md](./31-SpringBoot项目实战.md) |
| 第三十二章 | Spring Cloud 项目实战：单体拆微服务（Nacos、Feign、Seata、网关、SkyWalking、全链路压测） | [32-SpringCloud项目实战.md](./32-SpringCloud项目实战.md) |
| 第三十三章 | MySQL 系统学习：SQL 基础（DDL/DML/DQL）、索引、事务隔离、锁、主从复制、分库分表 | [33-MySQL.md](./33-MySQL.md) |
| 第三十四章 | Redis 系统学习：五大数据类型命令全解、持久化、哨兵集群、分布式锁、缓存治理实战 | [34-Redis.md](./34-Redis.md) |
| 第三十五章 | Docker 系统学习：镜像容器、数据卷、网络、Dockerfile、Compose、私有仓库、CI/CD、K8s 衔接 | [35-Docker.md](./35-Docker.md) |

## 学习路线

```
基础语法 ──► 面向对象 ──► 集合框架 ──► 异常/泛型 ──► 多线程 ──► IO/网络/数据库 ──► 综合实战 ──► 发送邮件 ──► Spring Boot ──► 工具库与日志 ──► 缓存 ──► 安全 ──► 设计模式
   │            │              │              │           │           │              │             │              │              │          │      │        │
   └────────────┴────── 务必动手敲代码，不要只看 ──────────┴───────────┴──────────────┴─────────────┴─────────────┴──────────────┴──────────┴──────┴────────┘

微服务 ──► 消息队列 ──► MyBatis-Plus ──► 任务调度 ──► 容器化 ──► 搜索引擎 ──► CI/CD 持续交付 ──► 网关与部署 ──► 分布式协调 ──► JVM 内功
   │          │             │           │           │          │             │                 │               │               │
   └────── 企业级进阶：按需选学，每一章都请动手实践 ──────────────┴─────────────────┴───────────────┴───────────────┴───────────────┘

MySQL 调优 ──► Netty 网络编程 ──► Kafka 大数据消息 ──► 设计原则深度 ──► Redis 深度篇 ──► 微服务治理实战
     │             │                  │                   │                 │                 │
     └────────── 深度专题：按需深挖，回归代码与架构本质 ──────────────────────┴─────────────────┴───────────────┘

Spring Boot 项目实战 ──► Spring Cloud 项目实战（全系列收官）
        │                        │
        └──── 终极整合：把前面 30 章的知识组装成完整项目 ────┘

专项精讲（按需深入，与实战项目配套使用）
MySQL 系统学习 ──► Redis 系统学习 ──► Docker 系统学习
     │                  │                  │
     └── 三驾马车：数据库/缓存/容器化，是每个后端工程师的必修课 ──┘
```

1. **阶段一（基础）**：第一章，掌握语法与编程思维，约 1~2 周。
2. **阶段二（核心）**：第二、三、四章，理解面向对象思想与常用 API，约 2~3 周。
3. **阶段三（进阶）**：第五、六章，掌握并发、IO、数据库操作，约 2 周。
4. **阶段四（实战）**：第七章，综合运用所学知识完成两个小项目，约 1 周。
5. **阶段五（扩展）**：第八章，掌握 JavaMail 邮件发送，约 2~3 天。
6. **阶段六（Web 开发）**：第九章，掌握 Spring Boot 企业级开发，约 2~3 周。
7. **阶段七（效率工具）**：第十章，掌握 Lombok 与 MapStruct，写代码事半功倍，约 3~4 天。
8. **阶段八（运维基础）**：第十一章，掌握日志框架与线上排障，约 3~4 天。
9. **阶段九（缓存）**：第十二章，掌握 Redis 与高并发缓存方案，约 1 周。
10. **阶段十（安全）**：第十三章，掌握认证授权与 JWT，约 1 周。
11. **阶段十一（内功）**：第十四章，掌握设计模式与框架原理，约 1 周。
12. **阶段十二（微服务）**：第十五章，掌握 Spring Cloud 微服务全家桶，约 2 周。
13. **阶段十三（消息中间件）**：第十六章，掌握 RabbitMQ 与异步解耦，约 1 周。
14. **阶段十四（数据层提速）**：第十七章，掌握 MyBatis-Plus，约 3~4 天。
15. **阶段十五（任务调度）**：第十八章，掌握 XXL-Job 分布式任务调度，约 3~4 天。
16. **阶段十六（容器化）**：第十九章，掌握 Docker 与 K8s 容器化部署，约 1 周。
17. **阶段十七（搜索引擎）**：第二十章，掌握 Elasticsearch 与 ELK，约 1 周。
18. **阶段十八（DevOps）**：第二十一章，掌握 Jenkins CI/CD 持续交付，约 3~4 天。
19. **阶段十九（网关部署）**：第二十二章，掌握 Nginx 反向代理、负载均衡与高可用，约 1 周。
20. **阶段二十（分布式协调）**：第二十三章，掌握 Zookeeper 原理与分布式锁，约 3~4 天。
21. **阶段二十一（JVM 内功）**：第二十四章，掌握 JVM 内存、GC 与线上调优排查，约 1 周。
22. **阶段二十二（数据库调优）**：第二十五章，掌握 MySQL 索引与 SQL 优化，约 1 周。
23. **阶段二十三（网络编程）**：第二十六章，掌握 Netty 与高并发网络模型，约 1 周。
24. **阶段二十四（大数据消息）**：第二十七章，掌握 Kafka 与流式消息架构，约 1 周。
25. **阶段二十五（设计收官）**：第二十八章，吃透 SOLID 原则并完成重构实践，约 3~4 天。
26. **阶段二十六（Redis 深度）**：第二十九章，掌握 Redis 原理、高可用与缓存治理，约 1 周。
27. **阶段二十七（架构治理）**：第三十章，掌握微服务治理全体系并完成订单系统治理实战，约 1 周。
28. **阶段二十八（单体实战）**：第三十一章，完成 Spring Boot 商城单体项目（防超卖、缓存、安全、部署），约 2~3 周。
29. **阶段二十九（微服务实战）**：第三十二章，完成单体拆微服务并实现全套治理，约 3~4 周。
30. **阶段三十（MySQL 专项精讲）**：第三十三章，系统掌握 SQL 基础、索引事务、主从分库，约 1 周。
31. **阶段三十一（Redis 专项精讲）**：第三十四章，掌握命令全解、高可用与缓存治理实战，约 1 周。
32. **阶段三十二（Docker 专项精讲）**：第三十五章，掌握镜像容器、Compose 与生产编排，约 1 周。

## 开发环境准备

### 1. 安装 JDK（Java Development Kit）

- 推荐使用 **JDK 8+**（本文示例基于 JDK 8/11/17 均可运行）。
- 官网下载：https://www.oracle.com/java/technologies/downloads/ （Oracle JDK）
- 开源替代：https://adoptium.net/ （Eclipse Temurin，推荐）
- 下载后一路默认安装，并记住安装路径（如 `C:\Program Files\Java\jdk-17`）。

### 2. 配置环境变量

以 Windows 为例：

```powershell
# 设置 JAVA_HOME（路径替换为你自己的 JDK 安装目录）
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-17", "User")

# 将 JDK 的 bin 目录加入 PATH
[Environment]::SetEnvironmentVariable("Path", "$env:Path;C:\Program Files\Java\jdk-17\bin", "User")
```

> 修改后需**重新打开**命令行窗口生效。

### 3. 验证安装

```powershell
java -version
javac -version
```

看到类似如下输出即安装成功：

```
java version "17.0.8" 2023-07-18 LTS
```

### 4. 推荐开发工具（IDE）

- **IntelliJ IDEA**（社区版免费，最推荐）
- **Eclipse**（免费）
- 也可以用 VS Code + Java 插件包

### 5. 编译与运行 Java 程序（命令行方式）

```powershell
# 1. 编写源码文件 Hello.java
# 2. 编译：生成 Hello.class 字节码文件
javac Hello.java
# 3. 运行：执行 main 方法（不需要 .class 后缀）
java Hello
```

> Java 程序运行机制：`源码(.java)` → 编译 `javac` → `字节码(.class)` → JVM 解释执行 → 跨平台。
> 一次编译，到处运行（Write Once, Run Anywhere）。

## 命名规范（重要）

| 类型 | 规范 | 示例 |
| --- | --- | --- |
| 项目名 | 全部小写 | `my-project` |
| 包名 | 全部小写，域名反写 | `com.example.demo` |
| 类名 | 大驼峰（首字母大写） | `StudentManager` |
| 方法名/变量名 | 小驼峰（首字母小写） | `getUserName` |
| 常量名 | 全大写，下划线分隔 | `MAX_COUNT` |

## 常用速查命令

```powershell
javac Hello.java          # 编译
java Hello                # 运行
javac -encoding UTF-8 Hello.java   # 指定编码编译（避免中文乱码）
java -cp . Hello          # 指定 classpath 运行
```

下一章：[01-基础语法.md](./01-基础语法.md)
