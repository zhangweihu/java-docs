# mall-cloud 商城微服务项目（Spring Cloud 实战）

> 配套学习文档：《32-SpringCloud项目实战.md》《30-微服务治理实战.md》。
> 本仓库将 mall-boot 单体演进为微服务：Nacos 注册/配置、Gateway 网关、
> Feign 服务调用、Seata 分布式事务，完整演示"单体 -> 微服务"的拆分过程。

## 一、架构总览

```
                    ┌────────────┐
   前端/App ──────► │ mall-gateway│ 统一入口：路由 / 鉴权 / 跨域 / 限流
                    └─────┬──────┘
                          │ lb:// 服务发现路由
        ┌─────────────────┼──────────────────┐
        ▼                 ▼                  ▼
   ┌─────────┐      ┌───────────┐      ┌──────────┐
   │mall-user│      │mall-product│      │ mall-order│
   │ 8081    │      │ 8082      │      │ 8083     │
   │注册/登录│      │商品/库存   │      │下单       │
   └────┬────┘      └─────┬─────┘      └────┬─────┘
        │                 │                 │
      MySQL              MySQL            MySQL       各服务独立数据库
    mall_user           mall_product     mall_order    (Seata undo_log)
        └───────────────┬─────────────────┘
                        │
        ┌───────────────┴───────────────┐
        │ Nacos(注册+配置) / Seata(事务) │ 基础设施
        └───────────────────────────────┘
```

跨服务调用示例：`mall-order` 通过 **Feign 契约**（定义在 mall-common）调用
`mall-product` 查价/扣库存，整个过程由 **Seata @GlobalTransactional** 保证跨库一致。

## 二、模块清单

| 模块 | 端口 | 职责 | 关键点 |
| --- | --- | --- | --- |
| mall-common | - | 公共模块 | 统一 Result/异常 + **Feign 契约**（DIP） |
| mall-gateway | 8080 | 统一入口 | 路由、统一鉴权（白名单+JWT）、跨域、**Sentinel 网关限流** |
| mall-user | 8081 | 用户服务 | 注册/登录发 JWT、/internal/** 内部接口 |
| mall-product | 8082 | 商品服务 | 商品浏览、乐观扣库存、内部价格接口、**Sentinel 流控** |
| mall-order | 8083 | 订单服务 | Feign 跨服务调用 + Seata 全局事务 + **Sentinel 流控演示** |

## 三、快速开始

```bash
# 1. 启动基础设施（MySQL 三库 + Redis + Nacos + Seata + Sentinel 控制台）
cd docker
docker compose up -d

# 1.1（可选）SkyWalking 可观测性（OAP + UI，UI http://localhost:8086）
docker compose -f docker/skywalking/docker-compose.yml up -d

# 1.2 发布 Sentinel 流控规则到 Nacos（规则配置中心化；幂等，可重复执行）
cd docker/nacos-config
bash init-sentinel-rules.sh        # Windows: .\init-sentinel-rules.ps1

# 2. 编译全部模块（先装 mall-common 供其他模块引用）
mvn clean install -DskipTests

# 3. 依次启动各服务（IDE 或命令行）
mvn -pl mall-gateway spring-boot:run
mvn -pl mall-user spring-boot:run
mvn -pl mall-product spring-boot:run
mvn -pl mall-order spring-boot:run

# 4. 验证：Nacos 控制台看到 4 个服务注册
#    http://localhost:8848/nacos   （账号 nacos/nacos）

# 5. 调用（网关统一入口）
curl http://localhost:8080/product/list

# 6. Sentinel 限流演示（QPS=2，连续刷新观察 429 兜底）
curl http://localhost:8080/order/sentinel/demo
#    Sentinel 控制台：http://localhost:8858 （sentinel/sentinel）
#    SkyWalking UI：  http://localhost:8086
```

## 四、目录结构（详细说明见 docs/）

```
mall-cloud/
├── README.md                  # 本文件
├── pom.xml                    # 父 POM：BOM 管理 + 5 个模块聚合
├── .gitignore
├── sql/                       # 三个服务独立建库脚本（含 Seata undo_log）
├── docker/                    # 基础设施编排（MySQL/Redis/Nacos/Seata/Sentinel）
│   ├── skywalking/            # SkyWalking OAP+UI 编排与探针接入指南
│   ├── nacos-config/          # Sentinel 规则配置中心化：规则 JSON + 发布脚本
│   └── service/Dockerfile     # 微服务通用镜像（内置 SkyWalking 探针）
├── k8s/                       # K8s 部署清单（infra + 4 服务 + ingress）
├── helm/                      # Helm Chart（基础设施 + 4 服务 + Ingress 一键部署）
├── docs/                      # 结构说明 + 文件说明
├── mall-common/               # 公共模块
├── mall-gateway/              # 网关
├── mall-user/                 # 用户服务
├── mall-product/              # 商品服务
└── mall-order/                # 订单服务
```

## 五、核心知识点索引

| 知识点 | 位置 |
| --- | --- |
| 注册中心 AP 模型/心跳 | mall-user 等 `application.yml` 的 nacos 配置 |
| 配置中心长轮询 | 各服务 `spring.config.import: nacos:xxx.yaml` |
| 网关统一鉴权 | `mall-gateway/.../AuthGlobalFilter.java` |
| Feign 契约（DIP） | `mall-common/.../feign/ProductClient.java` |
| Seata AT 分布式事务 | `mall-order/.../OrderServiceImpl.java` 的 @GlobalTransactional |
| 内部接口防暴露 | 各服务 `/internal/**` + 网关白名单不放行 |
| 独立数据库原则 | `sql/` 三个库各自建表 |
| Sentinel 限流（网关/接口两级） | 规则配置中心化：Nacos 数据源（`docker/nacos-config/`）+ 各服务 `@SentinelResource` 兜底 |
| SkyWalking 探针接入 | `docker/service/Dockerfile` + `docker/skywalking/` |
| K8s 部署 | `k8s/` 原生清单，或 `helm/` Chart 一键部署 |

## 六、常见问题

- **服务注册不上**：确认 Nacos 已启动，且 `NACOS_ADDR` 指向 localhost:8848，9848 端口可访问
- **Feign 调用失败**：检查提供方 `/internal/**` 接口路径与契约 `path` 是否一致
- **Seata 全局回滚不生效**：确认三个库都有 `undo_log` 表、seata-server 已注册到 Nacos、`tx-service-group` 一致
- **网关 401**：登录获取 Token 后，请求头带 `Authorization: Bearer <token>`
- **启动报 webflux/webmvc 冲突**：网关模块禁止引入 `spring-boot-starter-web`
- **Sentinel 不生效**：确认控制台 8858 可达、`spring.cloud.sentinel.transport.dashboard` 正确；**限流规则来自 Nacos**（group=`SENTINEL_GROUP`），先执行 `docker/nacos-config/init-sentinel-rules.sh` 发布规则，Nacos 修改后实时推送无需重启
- **SkyWalking 看不到链路**：确认探针 `-javaagent` 已生效（启动日志有 SkyWalking agent 字样）、OAP 11800 可达、且已产生业务流量
