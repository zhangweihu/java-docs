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
| 第三十六章 | Kong API 网关系统学习：核心对象、Admin API/declarative 配置、认证限流插件、负载均衡健康检查、DB-less 集群、K8s Ingress | [36-Kong网关.md](./36-Kong网关.md) |
| 第三十七章 | Nacos 系统学习：AP/CP 双模型与 Distro/Raft 协议、临时持久实例、命名空间隔离、配置长轮询、三节点集群 + MySQL、安全加固 | [37-Nacos.md](./37-Nacos.md) |
| 第三十八章 | 数据迁移系统学习：Flyway 原理与命令、Liquibase 对比、增量脚本规范、分批迁移、影子表、DataX/Canal、CI/CD 集成（配套 migration-demo 实操项目） | [38-数据迁移.md](./38-数据迁移.md) |
| 第三十九章 | 建表脚本系统学习：CREATE TABLE 完整语法、字段类型选型、主键与索引设计、公共字段模板、经典业务表模型、脚本组织与幂等写法、多库差异 | [39-建表脚本.md](./39-建表脚本.md) |
| 第四十章 | 贫血模型与充血模型（领域模型设计）：概念与四形态（失血/贫血/充血/胀血）、同场景代码对照、选型决策树、DDD 关系、订单模块贫血→充血实战改造、依赖倒置与领域服务 | [40-贫血模型与充血模型.md](./40-贫血模型与充血模型.md) |
| 第四十一章 | DDD 战术设计：限界上下文与通用语言、实体、值对象（Money）、聚合与聚合根（五条铁律）、领域服务与工厂、仓储（接口倒置 + MyBatis/JPA 两实现）、订单聚合四层实战、规格模式与防腐层 | [41-DDD战术设计.md](./41-DDD战术设计.md) |
| 第四十二章 | Spring StateMachine 状态机：FSM 五要素、声明式迁移表配置、Guard/Action、监听器、持久化（状态对齐/快照）、订单状态机实战、手写 if/状态模式/框架选型对照 | [42-SpringStateMachine.md](./42-SpringStateMachine.md) |
| 第四十三章 | CQRS 与事件溯源：命令查询分离三形态、事件即事实（append-only/重放/快照）、CQRS+ES 组合架构、账户转账手写实战（事件存储/投影/读模型）、事件版本兼容、选型决策树 | [43-CQRS与事件溯源.md](./43-CQRS与事件溯源.md) |
| 第四十四章 | 事件风暴工作坊实操：两大规模（Big Picture/Design Level）、六大元素（事件/命令/聚合/读模型/外部系统/规则）、八步时间线建模法、主持与控场技巧、电商订单实战（事件墙→聚合骨架→规则测试）、坑与误区、落地衔接 | [44-事件风暴工作坊实操.md](./44-事件风暴工作坊实操.md) |
| 第四十五章 | Axon Framework 实战：四大总线与 Gateway、@Aggregate/@CommandHandler/@EventSourcingHandler、事件投影与重放、@QueryHandler 读模型、@Saga 编排、Event Store 与快照、账户转账 Axon 实战、选型决策 | [45-AxonFramework实战.md](./45-AxonFramework实战.md) |
| 第四十六章 | 微服务下分布式事务与 Saga：CAP/BASE 一致性模型、六大方案（2PC/XA、TCC 三坑、本地消息表、RocketMQ 事务消息、Saga 编排/协同、Seata 四模式）、下单 Saga 状态机实战、选型决策树 | [46-分布式事务与Saga.md](./46-分布式事务与Saga.md) |
| 第四十七章 | 企业级架构专题（上）：架构演进路线、高并发四武器（缓存/异步/分库分表/限流熔断降级）、高可用（冗余/故障转移/多活容灾/RPO-RTO）、容量规划与压测、订单高并发改造实战 | [47-企业级架构专题（上）-高并发与高可用.md](./47-企业级架构专题（上）-高并发与高可用.md) |
| 第四十八章 | 企业级架构专题（下）：可观测性三支柱（日志/指标/链路追踪）、监控告警体系、灰度发布与回滚、SRE/SLO/混沌工程、架构治理与演进式架构 | [48-企业级架构专题（下）-可观测性与稳定性.md](./48-企业级架构专题（下）-可观测性与稳定性.md) |
| 第四十九章 | K8s 深度专题：控制面/数据面架构、控制器循环（Reconcile）、调度策略（亲和性/污点容忍）、CNI 网络与 Service、PV/PVC/StorageClass、RBAC 安全、Java 生产部署（探针/优雅停机/HPA/QoS）、生产排障表 | [49-K8s深度专题.md](./49-K8s深度专题.md) |
| 第五十章 | Service Mesh 与 Istio：治理三阶段演进、Sidecar 数据面 + istiod 控制面、VirtualService/DestinationRule/Gateway、金丝雀发布、熔断/重试/超时、mTLS 与授权、故障注入、与 Spring Cloud/Kong 边界选型 | [50-ServiceMesh与Istio.md](./50-ServiceMesh与Istio.md) |
| 第五十一章 | Serverless：FaaS/BaaS、事件驱动执行模型、冷启动成因与五大优化（GraalVM/预留实例/层）、Java 函数实战、Knative 自建、Serverless vs 容器选型决策 | [51-Serverless.md](./51-Serverless.md) |
| 第五十二章 | 行业解决方案案例：案例四步法（挑战/对策/选型/复盘）、六大行业实战（电商防超卖/金融账务一致性/物流实时轨迹/社交 Feed 流/游戏排行榜/大数据管道）、复盘五问与 STAR 面试话术 | [52-行业解决方案案例.md](./52-行业解决方案案例.md) |
| 第五十三章 | DevOps 与 GitOps 深度：DevOps 四大支柱、CI 深度（Pipeline as Code/构建提速/质量门禁）、CD 多环境与验证闭环、GitOps 三原则、ArgoCD/Argo Rollouts 金丝雀自动回滚、SealedSecrets/Vault、平台工程 IDP、端到端实战流水线 | [53-DevOps与GitOps深度.md](./53-DevOps与GitOps深度.md) |
| 第五十四章 | 云原生安全专项：六面攻击面与纵深防御、供应链安全（SBOM/Cosign/依赖扫描）、镜像与运行时加固、K8s 安全（RBAC 深度/准入控制/NetworkPolicy/Secrets）、零信任 mTLS、数据安全（KMS/脱敏）、DevSecOps 安全左移 | [54-云原生安全专项.md](./54-云原生安全专项.md) |
| 第五十五章 | 更多行业解决方案案例：新增六大行业（教育直播/医疗合规/智慧政务/制造 IoT/旅游库存/SaaS 多租户）、跨行业通用模式库（削峰/数据管道/多租户/状态机）、陌生行业三问分析法 | [55-更多行业解决方案案例.md](./55-更多行业解决方案案例.md) |
| 第五十六章 | K8s Operator 开发：Operator 模式、CRD/Reconcile 三原则、Java Operator SDK 实战（MySqlCluster 控制器/Finalizer/状态上报）、envtest 测试与 Helm/OLM 部署、生产最佳实践 | [56-K8sOperator开发.md](./56-K8sOperator开发.md) |
| 第五十七章 | 分库分表与 ShardingSphere 深度实战：拆分四象限、分片键三条铁律、分片策略与扩容成本、读写分离×分片组合架构、ShardingSphere-JDBC/Proxy 选型与订单实战、跨片查询/分页、分布式 ID（雪花）、跨片事务协同、踩坑与面试 | [57-分库分表与ShardingSphere深度实战.md](./57-分库分表与ShardingSphere深度实战.md) |
| 第五十八章 | 数据库高可用与容灾：RPO/RTO 度量体系、异步/半同步/MGR 复制深化、主从延迟治理、MHA/MGR/Orchestrator/代理层方案对比、应用层容错、跨机房多活与单元化、备份体系与 PITR、pt-table 数据校验 | [58-数据库高可用与容灾.md](./58-数据库高可用与容灾.md) |
| 第五十九章 | 大规模数据迁移与不停机扩容：七步迁移方法论、DataX 全量 + Canal 增量黄金组合、双写与灰度切换、影子表验证、扩容 rehash（翻倍扩容法）、对账校验、回滚预案与实战案例 | [59-大规模数据迁移与不停机扩容.md](./59-大规模数据迁移与不停机扩容.md) |
| 第六十章 | 分布式数据库与 NewSQL：NewSQL 三流派、TiDB 架构原理（Region/Raft/PD 三层分离）、Java 零改造接入、OceanBase 与国产数据库、终极选型决策树、数据专题收官 | [60-分布式数据库与NewSQL.md](./60-分布式数据库与NewSQL.md) |
| 第六十一章 | Flink 流式计算：批 vs 流本质、JobManager/TaskManager 架构与并行度、DataStream API、时间语义与 Watermark（乱序处理）、滚动/滑动/会话窗口、Checkpoint 与 exactly-once（两阶段提交）、Flink SQL 实时订单实战、Flink CDC、K8s 部署、Flink vs Spark vs Kafka Streams 选型 | [61-Flink流式计算.md](./61-Flink流式计算.md) |
| 第六十二章 | Flink 实时数仓分层实战：Lambda/Kappa 架构演进、ODS/DWD/DWS/ADS 四层设计与组件选型、Flink SQL 全链路实战（CDC 入 ODS/维表拉宽 DWD/窗口聚合 DWS/落库 ADS）、维表关联三方案、状态 TTL、实时离线对账 | [62-Flink实时数仓分层实战.md](./62-Flink实时数仓分层实战.md) |
| 第六十三章 | Spark 体系：内存计算与 MapReduce 对比、Driver/Executor 架构、RDD/DataFrame/Dataset 演进、DAG 宽窄依赖与 Shuffle、Spark SQL 与 Hive 离线数仓、Structured Streaming、数据倾斜三板斧、Spark vs Flink 选型 | [63-Spark体系.md](./63-Spark体系.md) |
| 第六十四章 | ClickHouse 与 Doris 分析引擎：OLTP/OLAP 与列存加速原理、ClickHouse MergeTree 分区与物化视图、Doris FE/BE MPP 与三数据模型、实时数仓 ADS 落地、MySQL/ES/CK/Doris 选型矩阵 | [64-ClickHouse与Doris分析引擎.md](./64-ClickHouse与Doris分析引擎.md) |
| 第六十五章 | 大数据平台全景：六层架构（采集/存储/计算/分析/治理/应用）、组件矩阵与规模选型、数仓vs数据湖vs湖仓一体、DolphinScheduler/Airflow 调度、元数据/血缘/质量/权限治理、从 0 到 1 搭建路线 | [65-大数据平台全景.md](./65-大数据平台全景.md) |
| 第六十六章 | Hive 深度实战：Hive 定位（HDFS 上的数仓工具）、Metastore 元数据、内部/外部表、分区与分桶、文件格式与压缩选型（Parquet/ORC+Snappy/Zstd）、一条 SQL 执行流程、HiveQL 加工实战、数据倾斜三板斧与小文件治理、Hive/Spark/Flink 分工 | [66-Hive深度实战.md](./66-Hive深度实战.md) |
| 第六十七章 | 数据湖 Iceberg 实战：表格式本质、快照 ACID 与并发控制、时间旅行与增量读取、分区/Schema 演进与隐藏分区、Flink 流式 UPSERT 入湖、Spark SQL 集成、Iceberg/Hudi/Delta 三选一、订单湖仓改造 | [67-数据湖Iceberg实战.md](./67-数据湖Iceberg实战.md) |
| 第六十八章 | 机器学习平台：ML 平台五层全景、特征平台与特征一致性、Spark MLlib 离线训练 + Flink 近线增量、模型服务（gRPC/PMML/ONNX）与 Java 集成、MLOps（实验/版本/灰度/漂移监控）、流失预警实战、推荐与大模型扩展 | [68-机器学习平台.md](./68-机器学习平台.md) |
| 第六十九章 | BI 可视化实践：BI 本质与工具选型（成品/开源/半自研）、指标体系（北极星/OSM）、指标平台统一口径、报表与大屏设计、自助分析、Java 后端 BI 架构（元数据→查询→缓存→权限→API）、数据讲故事、大数据专题 69 章收官 | [69-BI可视化实践.md](./69-BI可视化实践.md) |

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
MySQL 系统学习 ──► Redis 系统学习 ──► Docker 系统学习 ──► Kong API 网关 ──► Nacos 系统学习 ──► 数据迁移 ──► 建表脚本
     │                  │                  │                    │                    │               │           │
     └── 七驾马车：数据库/缓存/容器化/网关/注册配置/数据迁移/建表脚本，是每个后端工程师的必修课 ──┘

设计内功 ──► ⑭ 设计模式 ──► ㉘ 设计原则深度 ──► ㊵ 贫血模型与充血模型 ──► ㊶ DDD 战术设计 ──► ㊷ 状态机 ──► ㊸ CQRS/事件溯源 ──► ㊹ Axon ──► ㊻ Saga/分布式事务
     │                    │                        │                    │                 │            │               │            │
     └── 软件设计八连：招式（模式）→ 心法（原则）→ 领域建模（模型形态）→ DDD 落地（聚合/仓储）→ 状态机制化 → 事件流（CQRS/ES）→ 框架工程化（Axon）→ 跨服务一致性（Saga/分布式事务）──┘

事件风暴 ──► 企业级架构（㊼ 高并发高可用 / ㊽ 可观测性与稳定性）──► 云原生三连（㊾ K8s 深度 / ㊿ Service Mesh / 51 Serverless）──► 行业方案（52 / 55）──► 工程化收官（53 DevOps/GitOps / 54 云原生安全 / 56 K8s Operator）──► 数据专题四连（57 分库分表 / 58 高可用容灾 / 59 迁移扩容 / 60 NewSQL）──► 大数据专题九连（61 Flink / 62 实时数仓 / 63 Spark / 64 OLAP 引擎 / 65 平台全景 / 66 Hive / 67 Iceberg 数据湖 / 68 机器学习 / 69 BI 可视化，全系列收官）
     │                    │
     └── 业务建模入口：一墙便签画出领域模型（㊹）── 企业级能力链：扛得住 → 不会挂 → 看得见 → 敢变更 → 云原生落地 → 行业实战 → 工程化与扩展 → 数据治理 → 实时计算 → 平台全景 ──┘
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
33. **阶段三十三（API 网关专项精讲）**：第三十六章，掌握 Kong 网关对象模型、插件体系与云原生部署，约 3~4 天。
34. **阶段三十四（Nacos 专项精讲）**：第三十七章，吃透注册配置双模型、AP/CP 协议与集群部署，约 3~4 天。
35. **阶段三十五（数据迁移专项精讲）**：第三十八章，掌握 Flyway/Liquibase 版本化迁移与生产数据迁移方案，配套 migration-demo 实操项目，约 2~3 天。
36. **阶段三十六（建表脚本专项精讲）**：第三十九章，掌握工程化建表脚本（类型选型、主键索引设计、规范模板、幂等写法、多库差异），约 2~3 天。
37. **阶段三十七（领域模型设计）**：第四十章，吃透贫血模型与充血模型的本质区别、DDD 关系与实战改造，约 2~3 天。
38. **阶段三十八（DDD 战术设计）**：第四十一章，掌握实体/值对象/聚合/仓储四大构件并完成订单聚合实战，约 1 周。
39. **阶段三十九（状态机）**：第四十二章，掌握 Spring StateMachine 配置、Guard/Action、持久化与订单状态机实战，约 3~4 天。
40. **阶段四十（CQRS 与事件溯源）**：第四十三章，理解命令查询分离与事件流架构，完成账户转账 CQRS+ES 实战与选型判断，约 3~4 天。
41. **阶段四十一（事件风暴工作坊）**：第四十四章，掌握事件风暴方法论（八步时间线法）并完成一次真实业务建模工作坊，约 2~3 天。
42. **阶段四十二（Axon 实战）**：第四十五章，用 Axon Framework 完成 CQRS+ES+Saga 工程化项目（账户转账），约 1 周。
43. **阶段四十三（分布式事务与 Saga）**：第四十六章，吃透六大分布式事务方案（2PC/TCC/本地消息表/MQ 事务消息/Saga/Seata）并完成下单 Saga 状态机实战，约 1 周。
44. **阶段四十四（企业级架构·上）**：第四十七章，掌握高并发四武器与高可用容灾设计（RPO/RTO），完成订单改造与容量规划，约 1 周。
45. **阶段四十五（企业级架构·下）**：第四十八章，掌握可观测性三支柱、SRE/SLO/混沌工程与架构治理，约 1 周。
46. **阶段四十六（K8s 深度专题）**：第四十九章，掌握 K8s 控制面/数据面原理、控制器循环与调度、网络存储体系，完成 Java 生产级部署（滚动更新/HPA/优雅停机/排障），约 1~2 周。
47. **阶段四十七（Service Mesh）**：第五十章，掌握 Istio 架构与流量管理（金丝雀发布/熔断/mTLS）并完成 Java 服务接入实验，约 1 周。
48. **阶段四十八（Serverless）**：第五十一章，掌握函数计算与 Java 函数开发、冷启动优化与 Serverless/K8s 选型，约 3~4 天。
49. **阶段四十九（行业解决方案）**：第五十二章，用六大行业案例串联全部知识、掌握案例复盘方法论与面试话术，约 1 周。
50. **阶段五十（DevOps/GitOps 深度）**：第五十三章，掌握 GitOps/ArgoCD 声明式发布与"代码到生产"端到端流水线，约 1 周。
51. **阶段五十一（云原生安全专项）**：第五十四章，掌握供应链安全、K8s 加固与零信任 mTLS，为系统补齐安全纵深，约 3~4 天。
52. **阶段五十二（更多行业案例）**：第五十五章，掌握更多行业案例与跨行业通用模式库，练就陌生行业快速建模能力，约 3~4 天。
53. **阶段五十三（K8s Operator 开发）**：第五十六章，用 Java Operator SDK 开发一个自定义控制器并部署到集群，约 1 周。
54. **阶段五十四（分库分表深度实战）**：第五十七章，掌握拆分方法论、分片键铁律与 ShardingSphere 实战，完成订单分库分表改造，约 1 周。
55. **阶段五十五（数据库高可用与容灾）**：第五十八章，掌握复制深化、MHA/MGR 方案、跨机房多活与备份恢复体系（PITR），约 3~4 天。
56. **阶段五十六（数据迁移与扩容）**：第五十九章，掌握七步迁移方法论与"全量+增量+双写"不停机方案，完成一次模拟扩容演练，约 3~4 天。
57. **阶段五十七（分布式数据库与 NewSQL）**：第六十章，理解 TiDB 架构与零改造接入、掌握数据库终极选型决策树，约 2~3 天。
58. **阶段五十八（Flink 流式计算）**：第六十一章，掌握 Flink 架构与编程模型、时间/Watermark/窗口/容错，完成实时订单统计实战并掌握流引擎选型，约 1 周。
59. **阶段五十九（Flink 实时数仓分层实战）**：第六十二章，掌握 ODS/DWD/DWS/ADS 四层设计与 Flink SQL 全链路，完成订单实时数仓实战与维表关联优化，约 1 周。
60. **阶段六十（Spark 体系）**：第六十三章，掌握 RDD/DataFrame、DAG 与 Shuffle、数据倾斜调优，完成离线分析作业与 Spark/Flink 选型判断，约 3~4 天。
61. **阶段六十一（OLAP 分析引擎）**：第六十四章，掌握列存原理、ClickHouse MergeTree 与 Doris 架构，完成分析层落地与引擎选型，约 3~4 天。
62. **阶段六十二（大数据平台全景）**：第六十五章，掌握六层平台架构与治理体系，用"从 0 到 1"路线串联 61~65 章，约 3~4 天。
63. **阶段六十三（Hive 深度实战）**：第六十六章，掌握 Hive 架构与 Metastore、分区/分桶/格式选型、HiveQL 加工与调优，约 3~4 天。
64. **阶段六十四（数据湖 Iceberg 实战）**：第六十七章，掌握表格式原理、ACID/时间旅行/增量读，完成订单湖仓改造，约 3~4 天。
65. **阶段六十五（机器学习平台）**：第六十八章，掌握特征平台、模型服务与 Java 集成、MLOps，完成流失预警实战，约 1 周。
66. **阶段六十六（BI 可视化实践，全系列收官）**：第六十九章，掌握指标体系建设、指标平台与 Java BI 架构，完成一次 BI 落地实践，收官 69 章体系，约 3~4 天。

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
