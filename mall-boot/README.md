# mall-boot 商城单体项目（Spring Boot 实战）

> 配套学习文档：《31-SpringBoot项目实战.md》。本仓库是其可运行的代码实现，
> 覆盖分层规范、JWT 登录、缓存治理、防超卖下单、定时关单、Docker 部署等核心知识点。

## 一、技术栈

| 分类 | 技术 | 版本 | 说明 |
| --- | --- | --- | --- |
| 语言 | Java | 17 | |
| 框架 | Spring Boot | 3.2.5 | Web + Validation + Data Redis |
| ORM | MyBatis-Plus | 3.5.6 | 分页 / 逻辑删除 / 乐观锁 |
| 数据库 | MySQL | 8.0 | |
| 缓存 | Redis | 7.x | 缓存 / 分布式锁 / 购物车 |
| 鉴权 | JWT (jjwt) | 0.12.5 | 无状态登录 |
| 接口文档 | SpringDoc OpenAPI | 2.4.0 | http://localhost:8080/swagger-ui.html |
| 可观测性 | SkyWalking | 9.x | Java Agent 探针接入（可选） |
| 部署 | Docker / Compose / K8s / Helm | - | 分阶段构建 + 一键编排 + K8s 清单 + Helm Chart |

## 二、快速开始

```bash
# 1. 方式一：本地启动（需先装 MySQL/Redis，并执行 sql/mall.sql 建库）
mvn spring-boot:run

# 2. 方式二：Docker 一键部署（自动拉起 MySQL + Redis + 应用）
cd docker
docker compose up -d --build

# 3. 验证
curl http://localhost:8080/product/list
curl http://localhost:8080/api/redis/verify   # Redis 实际对接自检（六步全链路）

# 4.（可选）SkyWalking：OAP/UI + 探针自动上报
docker compose -f docker/docker-compose.yml -f docker/docker-compose.skywalking.yml up -d --build

# 5.（可选）Kubernetes：Helm 一键部署（MySQL + Redis + 应用 + Ingress）
cd helm/mall-boot
helm install mall-boot . --namespace mall-boot --create-namespace
```

启动成功后：
- 接口文档：http://localhost:8080/swagger-ui.html
- 演示数据：`t_product` 已内置 3 本图书商品

## 三、目录结构（详细说明见 docs/）

```
mall-boot/
├── README.md                  # 本文件：项目总览
├── pom.xml                    # Maven 构建配置
├── .gitignore                 # Git 忽略规则
├── sql/
│   └── mall.sql               # 建库建表 + 演示数据
├── docker/
│   ├── Dockerfile                 # 分阶段构建（含 SkyWalking 探针）
│   ├── docker-compose.yml         # MySQL+Redis+应用 一键编排
│   └── docker-compose.skywalking.yml  # （可选）叠加 SkyWalking OAP/UI
├── k8s/                       # K8s 部署清单（namespace/configmap/secret/mysql/redis/app/ingress）
├── helm/                      # Helm Chart（应用 + 可选 MySQL/Redis，一键部署）
├── docs/
│   ├── 项目结构说明.md          # 包结构 + 每个文件夹职责
│   └── 文件说明.md             # 每个文件逐一说明
└── src/
    ├── main/java/com/mall/    # 源码（分层架构）
    │   ├── common/            # 通用：Result/异常/全局处理器
    │   ├── config/            # 配置类
    │   ├── controller/        # 控制器层
    │   ├── service/           # 服务接口 + impl 实现
    │   ├── mapper/            # 数据访问层
    │   ├── entity/            # 实体类
    │   ├── dto/               # 入参对象
    │   ├── vo/                # 出参对象
    │   ├── util/              # 工具类
    │   ├── interceptor/       # 登录拦截器
    │   ├── runner/            # 启动自检（Redis 连通性）
    │   └── job/               # 定时任务
    └── main/resources/        # 配置文件
        ├── application.yml        # 主配置
        ├── application-dev.yml    # 开发环境
        └── application-prod.yml   # 生产环境
```

## 四、核心知识点索引

| 知识点 | 位置 |
| --- | --- |
| 统一返回体 + 全局异常 | `common/`（Result、GlobalExceptionHandler） |
| JWT 无状态登录 | `util/JwtUtil.java` + `interceptor/JwtInterceptor.java` |
| 缓存三件套（防击穿/穿透/雪崩） | `service/impl/ProductServiceImpl.java` |
| 下单三件套（事务+锁+乐观扣减） | `service/impl/OrderServiceImpl.java` |
| 购物车 Hash 存储 | `service/impl/CartServiceImpl.java` |
| 订单超时关单 | `job/OrderTimeoutJob.java` |
| 分环境配置 / 敏感信息外置 | `application-prod.yml` |
| Redis 实际对接验证 | `service/RedisVerifyService.java` + `GET /api/redis/verify` |
| SkyWalking 探针接入 | `docker/Dockerfile` + `docker-compose.skywalking.yml` |
| 容器化部署 | `docker/` + `k8s/` + `helm/` |

## 五、常见问题

- **端口被占用**：修改 `application.yml` 的 `server.port`
- **数据库连不上**：确认执行过 `sql/mall.sql`，且 dev 配置账号密码正确
- **登录 401**：请求头需带 `Authorization: Bearer <token>`
- **超卖疑问**：扣库存 SQL 带 `stock >= quantity` 条件 + 分布式锁双重保障，见 `OrderServiceImpl`
- **Redis 连不上**：先 `docker compose -f docker/docker-compose.yml up -d redis`，再调 `GET /api/redis/verify` 看哪一步失败；启动日志会打印 `[Redis 自检]` 结果
