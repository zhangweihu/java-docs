# 第三十六章 Kong API 网关系统学习

> 本章目标：理解 API 网关的定位与主流选型，系统掌握 Kong Gateway——核心对象模型（Service/Route/Upstream/Consumer/Plugin）、Docker 部署、Admin API 与声明式配置（deck/declarative）、认证/限流/安全/可观测性插件实战、负载均衡与健康检查、DB-less 与集群高可用，最后衔接 Kubernetes（Kong Ingress Controller）与 Java 微服务实战项目。
>
> 前置知识：第十五章 Spring Cloud（Spring Cloud Gateway 网关）、第十九章 Docker 与 K8s、第二十二章 Nginx、第三十五章 Docker 系统学习。

## 36.1 为什么需要 API 网关：从 Nginx 到 Kong

### 36.1.1 网关能统一解决什么问题

回顾第十五章：Spring Cloud Gateway 是**跑在 Java 进程里**的网关，只服务于自己这套微服务。当业务规模变大、技术栈变多（Java/Go/Node/Python 并存）、外部生态接入变多（移动端、第三方开放平台、合作伙伴），问题就来了：

| 问题 | 表现 |
| --- | --- |
| 每个服务都写鉴权/限流 | 代码重复，改一处要动 N 个服务 |
| 技术栈割裂 | Java 用 Spring Cloud Gateway，Go/Node 服务怎么办？ |
| 服务端口暴露 | 内部服务地址直接暴露公网，不安全 |
| 缺乏统一治理 | 没有统一入口做流控、熔断、灰度、审计 |
| 对接成本高 | 第三方接系统要学你们内部协议、签名规则 |

**API 网关**就是放在"客户端与后端服务"之间的**独立一层**，统一承担：路由转发、认证授权、限流熔断、请求/响应转换、日志监控、灰度发布等横切能力。它必须**与具体语言无关**，才能成为全公司的流量总入口。

### 36.1.2 一条演进路线：Nginx → OpenResty → Kong

```
Nginx           高性能 Web/反向代理/负载均衡，纯静态配置，改配置要 reload
  │
  ▼
OpenResty       Nginx + Lua 模块，能在请求各阶段跑 Lua 脚本（可编程）
  │
  ▼
Kong Gateway    基于 OpenResty，把"可编程"封装成：管理 API + 数据库 + 插件体系
                （写一个 Lua/Go 插件 = 增加一项网关能力，无需改网关本体）
```

> **一句话理解 Kong**：Kong 是"长了管理后台和插件商店的 Nginx"。数据面依然是 Nginx 内核（性能强悍），但它通过 Admin API 把路由、插件、消费者全部变成可动态配置的**对象**，改配置不用 reload、不用动一行 Java 代码。

### 36.1.3 Kong 与同类网关对比

| 维度 | **Kong** | Spring Cloud Gateway | Nginx | APISIX |
| --- | --- | --- | --- | --- |
| 定位 | 独立部署的云原生 API 网关 | 微服务进程内网关 | Web 服务器/反向代理 | 云原生 API 网关 |
| 内核/语言 | Nginx + OpenResty（Lua/Go 插件） | Java WebFlux（Netty 响应式） | C | Nginx + OpenResty |
| 部署形态 | 独立容器/进程，与应用解耦 | 跟随 Java 应用 | 独立进程 | 独立容器/进程 |
| 配置方式 | Admin API / declarative / DB | YAML + Java 代码 | 静态 conf 文件 | Admin API / etcd |
| 扩展方式 | 插件体系（认证/限流/转换等 100+） | 编写 Java 过滤器 | 改 conf + 重载 | 插件体系（Lua） |
| 动态变更 | 支持（改对象即生效） | 需刷新路由 | 需 reload | 支持 |
| 多语言接入 | 完美（只认 HTTP） | 仅限 Java 生态 | 一般 | 完美 |
| 开源协议 | Apache 2.0（社区版） | Apache 2.0 | 类 BSD | Apache 2.0 |

**选型建议**：
- 纯 Java 单体微服务、团队全是 Java → **Spring Cloud Gateway**（与 Spring 生态无缝）；
- 多技术栈、对外提供 API、需要平台化网关能力 → **Kong / APISIX**；
- 只需要反向代理 + 负载均衡 → **Nginx** 就够，别上重网关。

> 章节呼应：第十五章 15.5 已讲 Spring Cloud Gateway；第二十二章已讲 Nginx。本章聚焦 **Kong**，把它作为"Java 微服务之外的企业级统一网关"来学习，并能与 mall-cloud 项目配合。

## 36.2 Kong 核心对象模型（必须吃透）

Kong 管理的一切都是**对象**，通过 Admin API 增删改查。核心六件套：

| 对象 | 作用 | 类比 |
| --- | --- | --- |
| **Service** | 一个后端服务（对应一个上游 URL） | 接口分组 / 路由目标 |
| **Route** | 匹配请求的规则（路径/方法/Header），关联 Service | Nginx 的 location |
| **Upstream** | 服务实例组（虚拟池），做负载均衡 | Nginx upstream 块 |
| **Target** | Upstream 里的具体实例（ip:port） | upstream 里的 server |
| **Consumer** | 调用方身份（应用/用户），用于认证与限流 | API 的"账号" |
| **Plugin** | 挂载在对象上的增强能力（认证/限流/日志...） | 中间件/过滤器 |

```
客户端请求
   │  http://localhost:8000/order/1001
   ▼
[ Route（匹配路径 /order/**）]  ──►  [ Service：order-service ]
                                            │  url 指向
                                            ▼
                                    [ Upstream: order-upstream ]
                                            │  轮询/哈希/最少连接
                                            ▼
                          [ Target A: 10.0.0.1:8082 ] ─ [ Target B: 10.0.0.2:8082 ]
（Plugin 可挂在：Route / Service / Consumer / 全局，层层叠加生效）
```

**Plugin 挂载层级与优先级**：同一插件可挂在不同对象上，作用域从大到小：

```
全局（Global） > Service > Route > Consumer
      ①           ②       ③       ④
规则：请求同时命中的多个层级都会生效；同插件冲突时取较"具体"的层级。
```

> 理解口诀：**Route 负责"什么请求" → Service 负责"转发给谁" → Upstream/Target 负责"发给哪台" → Plugin 负责"路上做什么" → Consumer 负责"谁在调用"**。

## 36.3 安装与部署（Docker 方式）

Kong 默认需要数据库存配置（PostgreSQL）。3.x 同时支持 **DB-less（无数据库）** 模式，后面 36.8 讲。

### 36.3.1 Docker Compose 一键启动（Kong + PostgreSQL）

```yaml
# docker-compose.yml
version: '3.8'

services:
  kong-db:
    image: postgres:13
    environment:
      POSTGRES_DB: kong
      POSTGRES_USER: kong
      POSTGRES_PASSWORD: kong
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "kong"]
      interval: 5s
      timeout: 3s
      retries: 10

  kong:
    image: kong:3.5        # 社区版（CE）；企业版是 kong/kong-gateway
    depends_on:
      kong-db:
        condition: service_healthy
    environment:
      KONG_DATABASE: postgres          # 有 DB 模式
      KONG_PG_HOST: kong-db
      KONG_PG_USER: kong
      KONG_PG_PASSWORD: kong
      KONG_PG_DATABASE: kong
      KONG_PROXY_LISTEN: 0.0.0.0:8000, 0.0.0.0:8443 ssl
      KONG_ADMIN_LISTEN: 0.0.0.0:8001, 0.0.0.0:8444 ssl
      KONG_ADMIN_GUI_LISTEN: 0.0.0.0:8002   # Kong Manager UI（社区版开放）
    ports:
      - "8000:8000"    # 代理入口（HTTP）
      - "8443:8443"    # 代理入口（HTTPS）
      - "8001:8001"    # Admin API（管理接口）
      - "8002:8002"    # Kong Manager 管理界面
    command: >
      sh -c "kong migrations bootstrap && kong start"
```

启动：

```bash
docker compose up -d
# 验证：Admin API 在线
curl http://localhost:8001/status
# 输出示例：{"database":{"reachable":true},"server":{...}}
```

> 第一次启动 `kong migrations bootstrap` 会初始化数据库表结构；之后重启不要重复执行（会报错"already bootstrapped"）。

### 36.3.2 端口速查

| 端口 | 用途 | 说明 |
| --- | --- | --- |
| 8000 / 8443 | 代理（Proxy）入口 | 客户端访问网关走这里 |
| 8001 / 8444 | Admin API | 管理 Kong 对象的 REST 接口 |
| 8002 | Kong Manager UI | 网页版管理界面 |

### 36.3.3 本地起一个后端服务用于测试

```bash
# 用 Python 快速起一个"订单服务"（8082 端口），返回 JSON
python -m http.server 8082 --bind 0.0.0.0
# 或者直接用现成的：启动 mall-boot / mall-cloud 的订单服务（第 32 章）
```

## 36.4 核心操作：路由一个请求到后端

### 36.4.1 方式一：Admin API（REST，推荐入门）

```bash
# 1. 创建 Service（指向订单服务）
curl -X POST http://localhost:8001/services \
  --data name=order-service \
  --data url=http://host.docker.internal:8082
# 注意：容器内访问宿主机用 host.docker.internal（Windows/macOS Docker Desktop 支持）

# 2. 给 Service 挂 Route（匹配路径）
curl -X POST http://localhost:8001/services/order-service/routes \
  --data name=order-route \
  --data 'paths[]=/order' \
  --data 'strip_path=false'

# 3. 通过网关访问！8000 是代理端口
curl http://localhost:8000/order/
```

验证结果：请求 `http://localhost:8000/order/xxx` 被 Kong 转发到后端 8082 端口。

> **strip_path 详解**：`strip_path=true`（默认）会把匹配到的前缀去掉再转发，例如 `/order/1001` → 后端收到 `/1001`；`false` 则原样转发。真实项目按后端接口设计取舍，务必分清，这是排障高频坑。

### 36.4.2 方式二：声明式配置 declarative.yml + deck（推荐生产）

Kong 官方提供 **deck** 命令行工具，把全部配置写成 YAML，`deck sync` 一键下发，适合 **GitOps**（配置进 Git，可审计、可回滚）。

```yaml
# kong.yml
_format_version: "3.0"

services:
  - name: order-service
    url: http://host.docker.internal:8082
    routes:
      - name: order-route
        paths: ["/order"]
        strip_path: false
    plugins:                     # 挂载在 Service 上的插件
      - name: key-auth           # 认证插件
      - name: rate-limiting      # 限流插件
        config:
          minute: 60
          policy: local

consumers:
  - username: app1
    keyauth_credentials:         # 给消费者发放 API Key
      - key: my-secret-key-123
```

```bash
# 安装 deck（macOS/Linux/WSL）
brew install kong/deck/deck
# 或下载二进制：https://github.com/Kong/deck/releases

# 校验格式（不实际下发）
deck validate --kong-addr http://localhost:8001 -s kong.yml
# 下发配置
deck sync --kong-addr http://localhost:8001 -s kong.yml
# 查看当前配置与文件的差异
deck diff --kong-addr http://localhost:8001 -s kong.yml
```

### 36.4.3 完整验证链路

```bash
# 不带 key 访问 → 401 Unauthorized（key-auth 已生效）
curl -i http://localhost:8000/order/

# 带 key 访问 → 200 OK
curl -i http://localhost:8000/order/ \
  -H "apikey: my-secret-key-123"

# 1 分钟超过 60 次 → 429 Too Many Requests（rate-limiting 生效）
```

> **Admin API vs declarative 的选择**：两者可以混用（deck 官方也支持 dump 现有配置再改）。入门用 Admin API 直观；生产强烈建议 declarative + deck 进 Git 版本管理。

## 36.5 插件实战：认证、限流、安全、转换、日志

Kong 的价值主要在 **Plugin（插件）** 体系。社区版自带 100+ 插件，全部按 `name` + `config` 配置，无需写代码。本节演示 5 大类别（括号内为真实插件名）。

### 36.5.1 认证（Authentication）

| 插件 | 原理 | 适用 |
| --- | --- | --- |
| `key-auth` | 请求头/Query 带 API Key | 最简单的机器身份 |
| `jwt` | 校验 JWT 签名与过期时间 | 对接第十三章 JWT 体系 |
| `basic-auth` | 用户名密码 | 服务端间简单认证 |
| `oauth2` | 完整 OAuth2.0 授权码/客户端模式 | 开放平台 |
| `hmac-auth` | HMAC 签名防篡改 | 高安全要求 |
| `ldap-auth` / `openid-connect` | 企业账号体系 | 单点登录 |

**配置 JWT 校验（衔接第十三章）**：

```bash
# 在 Service 上开启 jwt 插件
curl -X POST http://localhost:8001/services/order-service/plugins \
  --data name=jwt

# 为消费者 app1 绑定一对 RSA 密钥
curl -X POST http://localhost:8001/consumers/app1/jwt \
  --data algorithm=RS256 \
  --data "rsa_public_key=-----BEGIN PUBLIC KEY-----\nMIIBIjANBg...\n-----END PUBLIC KEY-----"

# 之后请求必须带：Authorization: Bearer <JWT>
```

### 36.5.2 限流与流量控制（Traffic Control）

| 插件 | 作用 |
| --- | --- |
| `rate-limiting` | 固定窗口限流（秒/分/小时/天） |
| `rate-limiting-advanced` | 高级版：滑动窗口、Redis 支持（企业版） |
| `request-size-limiting` | 限制请求体大小，防大包攻击 |
| `request-termination` | 直接终止请求返回自定义响应（下线路由常用） |
| `proxy-cache` | 响应缓存，减轻后端压力 |

```bash
# 限流：每分钟最多 60 次
curl -X POST http://localhost:8001/services/order-service/plugins \
  --data name=rate-limiting \
  --data config.minute=60 \
  --data config.policy=local
```

> `policy` 三选一：`local`（单节点内存，性能好）；`cluster`（基于数据库，多节点共享）；`redis`（推荐生产，基于 Redis 计数器，多节点一致且性能高）。

### 36.5.3 安全（Security）

| 插件 | 作用 |
| --- | --- |
| `acl` | 访问控制列表：只有指定 Consumer 组可访问 |
| `cors` | 跨域配置（前端对接必备） |
| `ip-restriction` | IP 黑白名单 |
| `bot-detection` | 检测并拦截爬虫 |
| `request-validator` | 按 JSON Schema 校验请求体 |

```bash
# 开启 CORS（前端跨域调接口）
curl -X POST http://localhost:8001/routes/order-route/plugins \
  --data name=cors \
  --data "config.origins[]=https://admin.mall.com" \
  --data "config.methods[]=GET" \
  --data "config.methods[]=POST" \
  --data config.headers=Authorization \
  --data "config.exposed_headers[]=X-User-Id"

# ACL：只有 app1 所在的 dev 组能访问
curl -X POST http://localhost:8001/services/order-service/plugins \
  --data name=acl \
  --data config.allow=dev \
  --data config.hide_groups_header=true
curl -X POST http://localhost:8001/consumers/app1/acls \
  --data group=dev
```

### 36.5.4 请求/响应转换（Transformation）

| 插件 | 作用 |
| --- | --- |
| `request-transformer` | 改请求：增删改 Header/Query/路径/请求体 |
| `response-transformer` | 改响应：增删改 Header/响应体 |
| `correlation-id` | 自动生成/透传 traceId（衔接第十一章日志 MDC） |

```bash
# 给所有下游请求统一注入调用来源头 + 去除内部头
curl -X POST http://localhost:8001/services/order-service/plugins \
  --data name=request-transformer \
  --data "config.add.headers[]=X-Source:gateway" \
  --data "config.remove.headers[]=X-Internal-Secret"
```

### 36.5.5 日志与可观测性（Observability）

| 插件 | 作用 |
| --- | --- |
| `file-log` / `http-log` | 访问日志写文件或 POST 到日志平台 |
| `tcp-log` / `udp-log` | 日志转发到 Logstash/Fluentd（衔接 ELK） |
| `statsd` / `prometheus` | 指标上报（Prometheus 采集 + Grafana 面板） |
| `zipkin` / `opentelemetry` | 链路追踪（衔接 SkyWalking/Zipkin 体系） |

```bash
# 开启 Prometheus 指标：http://localhost:8001/metrics 可直接被 Prometheus 抓取
curl -X POST http://localhost:8001/plugins \
  --data name=prometheus
```

## 36.6 负载均衡与健康检查

### 36.6.1 Upstream + Target 实现负载均衡

```bash
# 1. 创建 Upstream（虚拟服务池）
curl -X POST http://localhost:8001/upstreams \
  --data name=order-upstream \
  --data algorithm=round-robin     # round-robin / consistent-hashing / least-connections

# 2. 添加两个 Target（订单服务两个实例）
curl -X POST http://localhost:8001/upstreams/order-upstream/targets \
  --data target=10.0.0.1:8082 \
  --data weight=100                # 权重，默认 100
curl -X POST http://localhost:8001/upstreams/order-upstream/targets \
  --data target=10.0.0.2:8082

# 3. 把 Service 指向 Upstream（而不是具体 url）
curl -X POST http://localhost:8001/services \
  --data name=order-service \
  --data host=order-upstream       # host 写 Upstream 名字
curl -X POST http://localhost:8001/services/order-service/routes \
  --data name=order-route \
  --data 'paths[]=/order'

# 4. 连续访问多次，Kong 按算法分发到两个实例
for i in $(seq 1 10); do curl -s http://localhost:8000/order/; echo; done
```

### 36.6.2 健康检查（Health Check）

Kong 支持**主动**（主动探测实例）与**被动**（根据真实请求的响应码判断）两种：

```bash
curl -X PATCH http://localhost:8001/upstreams/order-upstream \
  --data "config.healthchecks.active.http_path=/health" \
  --data "config.healthchecks.active.healthy.interval=10" \
  --data "config.healthchecks.active.unhealthy.interval=10" \
  --data "config.healthchecks.passive.unhealthy.http_failures=3" \
  --data "config.healthchecks.passive.unhealthy.timeouts=3"
```

配置后：
- 主动检查：每 10 秒访问每个 Target 的 `/health`，失败阈值达到后标记 **unhealthy**；
- 被动检查：真实请求连续失败 3 次，自动摘除该 Target；
- 恢复后自动放回（healthy 判定），无需人工干预。

> 配合第八节，这是生产环境"自愈 + 平滑摘除故障实例"的关键配置，等价于 K8s 的 readinessProbe 思路。

## 36.7 管理界面：Kong Manager 与 Konga

- **Kong Manager**：Kong 官方 UI（前面 docker-compose 已开放 8002 端口），浏览器访问 `http://localhost:8002`，可视化管理 Service/Route/Consumer/Plugin。
- **Konga**：第三方开源管理面板（更轻量美观）：`docker run -d -p 1337:1337 pantsel/konga`，首次进入配置 Kong Admin URL 为 `http://<kong-ip>:8001` 即可。

> 建议：界面只用来**查看**，配置变更走 deck/Admin API，保证可审计。

## 36.8 高可用与生产部署形态

### 36.8.1 有 DB 模式（传统）

```
        ┌─────────────┐   ┌─────────────┐
        │  Kong Node 1 │   │  Kong Node 2 │   数据面：无状态，可水平扩展
        └──────┬──────┘   └──────┬──────┘
               │                 │
        ┌──────┴─────────────────┴──────┐
        │        PostgreSQL（主从/集群）   │   控制面：存配置，主从高可用
        └────────────────────────────────┘
```

- 多个 Kong 节点共用一个数据库，配置即时同步，**节点无状态、随便加**；
- 前置 LB（Nginx/云 SLB）把流量分到多个 Kong 节点；
- 数据库做 PostgreSQL 主从/Patroni 高可用，避免单点。

### 36.8.2 DB-less 模式（云原生推荐）

不连数据库，Kong 启动时从 declarative.yml 加载全部配置，**运行时不存储状态**：

```bash
docker run -d --name kong-dbless \
  -p 8000:8000 -p 8001:8001 \
  -v $(pwd)/kong.yml:/kong.yml \
  -e KONG_DATABASE=off \
  -e KONG_DECLARATIVE_CONFIG=/kong.yml \
  -e KONG_ADMIN_LISTEN=127.0.0.1:8001 \
  kong:3.5
```

| 对比 | 有 DB 模式 | DB-less 模式 |
| --- | --- | --- |
| 配置存储 | PostgreSQL | 本地 yml 文件 |
| 动态变更 | Admin API 即时生效 | 改文件 + reload（`kong reload` 或 K8s 滚动重启） |
| 运维复杂度 | 多一个数据库要维护 | 更简单 |
| 适合场景 | 传统 VM、需要热变更 | K8s、GitOps、配置即代码 |

### 36.8.3 Kong Ingress Controller（K8s 里的用法，与第 19/35 章衔接）

Kong 官方提供 **KIC（Kong Ingress Controller）**，让 Kong 直接充当 Kubernetes 的 Ingress 控制器——把 K8s 的 `Ingress` 资源自动翻译成 Kong 的路由配置：

```bash
# 用 Helm 一键部署（第 35 章已学 Helm）
helm repo add kong https://charts.konghq.com
helm repo update
helm install kong kong/ingress --namespace kong --create-namespace
```

```yaml
# ingress.yaml：为 mall-cloud 的订单服务配置网关入口
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: mall-order-ingress
  annotations:
    konghq.com/strip-path: "false"        # Kong 专有注解，等价于 strip_path
    kubernetes.io/ingress.class: kong
spec:
  ingressClassName: kong                   # 指定由 Kong 处理
  rules:
    - host: api.mall.com
      http:
        paths:
          - path: /order
            pathType: Prefix
            backend:
              service:
                name: order-service
                port:
                  number: 8082
```

```bash
kubectl apply -f ingress.yaml
# 之后访问 http://api.mall.com/order/... 即被 Kong 转发到 order-service
```

KIC 还支持 `KongPlugin` 自定义资源，把限流/认证插件用 K8s 的方式声明：

```yaml
# kongplugin.yaml：声明一个限流插件资源
apiVersion: configuration.konghq.com/v1
kind: KongPlugin
metadata:
  name: rate-limit-60
config:
  minute: 60
  policy: local
plugin: rate-limiting
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: mall-order-ingress
  annotations:
    konghq.com/plugins: rate-limit-60    # 把插件挂到这个 Ingress 上
```

> 至此形成完整闭环：**Kong 既能当 K8s 的 Ingress，也能当独立 API 网关**，一套能力两处复用，这也是云原生网关成为主流的原因。

## 36.9 与 Java 微服务实战的集成（mall-cloud 视角）

### 36.9.1 位置与分工：谁在前、谁在后

```
客户端 ──► [Kong 网关（统一入口：认证/限流/审计/灰度）]
                 │  http://api.mall.com/order/...
                 ▼
          [Nacos 注册中心]
                 │
                 ▼
   ┌───────────────────────────────────────────┐
   │ mall-cloud 内部：Spring Cloud Gateway 也可以继续存在  │
   │（若保留：负责内部路由聚合；若去掉：Feign 直连服务）       │
   │  user-service │ order-service │ product-service │
   └───────────────────────────────────────────┘
```

两种常见架构：
1. **Kong 替换 Spring Cloud Gateway**：微服务只注册 Nacos，Kong 负责对外全部网关职责（第 15/32 章可去掉 Gateway 模块，用 Kong + deck 管理路由）；
2. **双层网关**：外层 Kong 管"公网流量治理"（防攻击、限流、多租户），内层 Spring Cloud Gateway 管"服务路由聚合"，各司其职。

### 36.9.2 对接要点

- 服务间 Feign 调用**不走 Kong**（Kong 只收外部流量，内部直连 Nacos 服务名）；
- JWT 校验可下放到 Kong（`jwt` 插件），业务服务只信任 Kong 注入的 `X-User-Id` 头（与第 15 章网关透传 userId 的思路一致）；
- 灰度发布：Kong 的 `request-termination` / `canary` 类插件 + Upstream 权重即可实现按比例灰度。

## 36.10 练习与总结

### 练习题（动手实操）

```bash
# 1. docker compose 拉起 Kong + PostgreSQL（36.3），curl :8001/status 验证在线
# 2. 用 Admin API 创建 Service/Route，把本地 8082 端口服务接入 :8000（36.4）
# 3. 给 Service 挂 key-auth + rate-limiting，验证 401 与 429（36.5）
# 4. 用 deck dump 出当前配置，改完再 sync，走一遍 GitOps 流程（36.4）
# 5. 创建 Upstream + 两个 Target（起两个后端实例），观察轮询分发（36.6）
# 6. 配置主动健康检查，停掉一个实例，观察 Kong 自动摘除（36.6）
# 7. 在 K8s 集群用 Helm 部署 KIC，给 mall-cloud 的 order-service 写 Ingress + KongPlugin（36.8）
```

### 面试高频问题清单

| 问题 | 答案要点 |
| --- | --- |
| Kong 与 Nginx 的关系？ | Kong 基于 OpenResty（Nginx+Lua），在 Nginx 之上加了 Admin API、数据库、插件体系 |
| Kong 核心对象有哪些？ | Service / Route / Upstream / Target / Consumer / Plugin |
| Route 的 strip_path 是什么？ | 是否剥离匹配前缀再转发，决定后端收到什么路径 |
| 有 DB 与 DB-less 模式区别？ | 配置存 DB（热变更） vs 存文件（GitOps）；K8s 场景多用 DB-less |
| Kong 插件机制原理？ | OpenResty 的 access/header_filter/body_filter 等阶段钩子，Lua/Go 实现 |
| 如何限流？ | rate-limiting 插件，policy 选 local/cluster/redis |
| 如何实现认证？ | key-auth / jwt / basic-auth / oauth2 等插件，配合 Consumer |
| Kong 怎么做负载均衡？ | Upstream + Target，round-robin / consistent-hashing / least-connections |
| 健康检查有哪两种？ | 主动探测（active）与被动统计（passive） |
| KIC 是什么？ | Kong Ingress Controller，把 K8s Ingress 翻译成 Kong 路由，支持 KongPlugin CRD |
| 网关层鉴权后如何透传用户身份？ | 校验 JWT 后注入 X-User-Id 头给下游（与第 15 章 Gateway 过滤器一致） |

### 本章小结

- **定位**（36.1）：Kong = 长在 Nginx/OpenResty 上的云原生 API 网关，与 Spring Cloud Gateway / Nginx / APISIX 的选型对比；
- **对象模型**（36.2）：Service/Route/Upstream/Target/Consumer/Plugin 六件套，插件分层挂载；
- **部署**（36.3~36.4）：Docker Compose 拉起 Kong+PostgreSQL，Admin API 与 declarative+deck 两种配置方式；
- **插件**（36.5）：认证、限流、安全、转换、日志五类插件即插即用，无需改代码；
- **生产**（36.6~36.8）：Upstream 负载均衡与健康检查、Kong Manager/Konga、有 DB/DB-less/集群高可用、K8s KIC；
- **实战衔接**（36.9）：与 mall-cloud 双网关或替换 Gateway 的架构方案。

配套：第十五章（Spring Cloud Gateway 对比）、第十九/三十五章（容器与 K8s 部署）、第二十二章（Nginx 基础）、第二十一章（CI/CD 集成）、第 30/32 章（微服务治理与实战项目）。

---

至此，第 36 章 Kong API 网关完成。返回：[README.md](./README.md)
