# 第五十章 Service Mesh 与 Istio（微服务治理的第三阶段）

> 本章目标：理解微服务治理为何从"SDK 内置"演进到"边车（Sidecar）外置"的服务网格模式，掌握 Istio 的核心架构（数据面 Envoy + 控制面 istiod）、流量管理（VirtualService/DestinationRule/Gateway）、安全（mTLS/授权策略）与可观测性，能在一套 K8s 集群中为 Java 微服务接入 Istio 并完成金丝雀发布与故障注入实验，理解它与 Spring Cloud/Kong 的边界与选型。
>
> 前置知识：第十五章 Spring Cloud、第三十章微服务治理、第三十六章 Kong 网关、第四十九章 K8s 深度专题（Service/Deployment/Ingress）、第四十八章可观测性。

## 50.1 为什么需要服务网格：微服务治理的三阶段

### 50.1.1 从 SDK 到边车的演进逻辑

微服务化后，每个服务都要实现一套"治理能力"：服务发现、负载均衡、熔断、重试、超时、限流、鉴权、链路追踪。这三代做法完全不同：

| 阶段 | 做法 | 代表 | 问题 |
| --- | --- | --- | --- |
| 第一代：集中式 | 所有流量经过统一网关 | ESB/API 网关 | 单点、性能瓶颈、网关变成"上帝服务" |
| 第二代：SDK 内置 | 治理逻辑打进每个服务的 SDK | Spring Cloud（Feign/Ribbon/Sentinel） | 语言绑定（只有 Java）、升级 SDK=全量发版、多语言团队没法统一 |
| 第三代：边车外置 | 治理逻辑下沉到 Sidecar 代理 | **Istio / Linkerd** | 多语言通吃、升级与业务解耦 |

> **一句话记忆**：服务网格 = 把"服务发现、流量治理、安全、可观测"从业务代码里**全部搬出来**，塞进每个 Pod 旁边的一个 Envoy 代理（Sidecar）里，由统一控制面下发规则。

### 50.1.2 Sidecar 模式：Pod 里的"隐形网关"

```
             ┌─────────────── Pod ───────────────┐
用户请求 ──►  │  Envoy(Sidecar) ──► Java 业务容器  │ ──►  Envoy(Sidecar) ──► 目标服务
             │   入站流量拦截/治理     │               │    出站流量治理        │
             └────────────────────────────────────┘
                    业务代码零改动（无侵入）
```

- 注入方式：K8s 的 **Webhook 自动注入**——创建 Pod 时自动把 Envoy 容器加进去，业务 YAML 不用改；
- Java 服务**一行代码不用动**，就获得熔断、重试、灰度、mTLS、追踪能力。

## 50.2 Istio 核心架构：数据面与控制面

```
                        ┌──────────────── 控制面 ────────────────┐
                        │  istiod（一体化控制面）                  │
                        │   ├─ Pilot：下发服务发现与流量规则        │
                        │   ├─ Citadel：证书签发（mTLS）           │
                        │   └─ Galley：配置校验与分发               │
                        └───────────────┬────────────────────────┘
                                        │ xDS（数据下发协议）
        ┌───────────────┬───────────────┴───────────────┬───────────────┐
        ▼               ▼                               ▼               ▼
┌─────────────┐  ┌─────────────┐               ┌─────────────┐  数据面
│ Pod: Envoy  │  │ Pod: Envoy  │               │ Pod: Envoy  │  （Sidecar）
│   业务容器   │  │   业务容器   │               │   业务容器   │
└─────────────┘  └─────────────┘               └─────────────┘
```

- **数据面**：每个服务旁边的 Envoy Sidecar，负责实际转发流量、执行规则、采集遥测数据；
- **控制面**：istiod 汇总集群里所有 Service/Endpoint，把"流量该往哪走、要不要重试、要不要 mTLS"翻译成 Envoy 能懂的 xDS 配置下发给每个 Sidecar；
- **好处**：业务方只声明 `VirtualService`（我想怎么路由），控制面负责翻译下发，Sidecar 负责执行。

> 与第 49 章呼应：K8s Service 解决"有哪些实例、怎么负载均衡"，Istio 在 Service 之上再叠一层"更细的流量策略"（按版本、按 Header、按比例灰度）。

## 50.3 流量管理：VirtualService / DestinationRule / Gateway

### 50.3.1 三个核心 CRD 的关系

```
Gateway（集群入口：暴露哪些域名/端口，可对接 Ingress/Kong/LB）
    │
    ▼
VirtualService（路由规则：请求怎么走 → 按 host/path/header 路由到目标服务）
    │  引用
    ▼
DestinationRule（目标策略：针对服务的子集定义负载均衡/熔断/连接池/mTLS）
    │  定义
    ▼
Service 的 Subset（按 label 划分版本：v1 / v2）
```

### 50.3.2 金丝雀发布（灰度）实战配置

假设 `mall-order` 服务有 `v1`（稳定）和 `v2`（新版本）两个子集，先放 10% 流量给 v2：

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: mall-order-vs
spec:
  hosts: ["mall-order"]
  http:
    - match:                                    # ① Header 灰度：指定用户先体验
        - headers:
            x-canary:
              exact: "true"
      route:
        - destination: { host: mall-order, subset: v2 }
    - route:                                    # ② 按比例灰度：90% v1 / 10% v2
        - destination: { host: mall-order, subset: v1, weight: 90 }
        - destination: { host: mall-order, subset: v2, weight: 10 }
---
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: mall-order-dr
spec:
  host: mall-order
  subsets:
    - name: v1
      labels: { version: v1 }
    - name: v2
      labels: { version: v2 }
  trafficPolicy:
    connectionPool:
      tcp: { maxConnections: 100 }              # 连接池限制（替代代码里调参）
    loadBalancer:
      simple: LEAST_REQUEST                     # 负载均衡策略下沉
    outlierDetection:                            # 熔断：连续失败自动摘除实例
      consecutive5xxErrors: 5
      interval: 30s
      baseEjectionTime: 60s
```

> **对比第 15/30 章**：Sentinel/Ribbon 的熔断限流写在 Java 代码或 SDK 里；Istio 把同样的能力用 YAML 声明，**对代码零侵入**，且规则变更即时生效（Sidecar 自动收到新配置）。

### 50.3.3 超时与重试（Java 调用方省掉一堆代码）

```yaml
http:
  - route:
      - destination: { host: mall-order, subset: v1 }
    timeout: 3s                    # 调用超时
    retries:
      attempts: 3
      retryOn: connect-failure,5xx # 连接失败/5xx 自动重试
```

## 50.4 安全：mTLS 与授权策略

### 50.4.1 mTLS：服务间双向加密认证

```
业务容器 ──► Envoy(证书A) ═════TLS 双向认证══════► Envoy(证书B) ──► 业务容器
                 │                                    │
             身份 = 证书（Citadel 自动签发，自动轮换）
```

- Istio 自动给每个服务签发证书并注入 Envoy，服务间流量自动升级为 mTLS——**应用无感**；
- 解决"内网也不可信"问题：即使有人进了内网，也无法伪造身份调用你的服务。

### 50.4.2 AuthorizationPolicy：服务级鉴权

```yaml
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: order-policy
  namespace: mall
spec:
  selector:
    matchLabels: { app: mall-order }
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/mall/sa/mall-account"]  # 只允许 account 服务调用
      to:
        - operation:
            methods: ["POST"]
            paths: ["/api/order/*"]
```

## 50.5 可观测性：Istio 的三支柱即开即用

| 能力 | 实现 | 说明 |
| --- | --- | --- |
| 指标 | Prometheus（Istio 自带抓取） | 每个服务/每个 Sidecar 的 QPS、延迟、错误率、TCP 连接数 |
| 链路追踪 | Jaeger/Zipkin（Istio 自动注入 span） | 业务代码不埋点也能看到全链路 |
| 访问日志 | Envoy Access Log | 每个请求的源/目标/状态/耗时 |

> 与第 48 章呼应：接入 Istio 后，可观测性数据**免费获得**——因为所有流量都经过 Envoy，它天然就是"观测点"。Java 侧只需保留 traceId 透传习惯即可。

## 50.6 落地实战：给 Java 服务接入 Istio

### 50.6.1 接入五步

```
1. 安装：istioctl install --set profile=default
2. 开启自动注入：kubectl label namespace mall istio-injection=enabled
3. 部署业务：kubectl apply -f deploy.yaml（Webhook 自动塞入 Envoy）
4. 声明流量规则：VirtualService / DestinationRule
5. 验证：kubectl exec 进 Pod 看两个容器；istioctl dashboard 看流量拓扑
```

### 50.6.2 验证是否注入成功

```bash
kubectl get pod -n mall -l app=mall-order -o jsonpath='{.items[0].spec.containers[*].name}'
# 输出：order istio-proxy     ← 出现 istio-proxy 即注入成功
istioctl proxy-status          # 查看所有 Sidecar 与控制面的连接状态
```

### 50.6.3 故障注入实验（混沌工程第 48 章的 Istio 版本）

```yaml
# 给 10% 的流量注入 5s 延迟，测试下游超时与重试策略是否生效
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: mall-order-fault
spec:
  hosts: ["mall-order"]
  http:
    - fault:
        delay:
          percentage: { value: 10 }
          fixedDelay: 5s
      route:
        - destination: { host: mall-order, subset: v1 }
```

## 50.7 边界与选型：Istio vs Spring Cloud vs Kong

### 50.7.1 三者定位不同，不是替代关系

| 层 | 工具 | 管什么 |
| --- | --- | --- |
| 南北向流量（外部进集群） | Ingress/Kong/云 LB | 域名、路由、WAF、外部限流 |
| **东西向流量（服务间）** | **Istio（Service Mesh）** | 内部调用治理：灰度、熔断、重试、mTLS |
| 应用层治理 | Spring Cloud/Sentinel | 业务级规则：库存维度限流、业务兜底 |

> **一句话记忆**：Kong 管"门口"，Istio 管"楼内电梯"，Spring Cloud 管"楼内业务规则"。Istio 与 Spring Cloud 不是二选一——Istio 抢走的是 Feign/Ribbon/Sentinel 里的**通用网络治理**，业务限流降级仍留在代码里。

### 50.7.2 什么时候用 / 什么时候别用

| 场景 | 建议 |
| --- | --- |
| 多语言微服务（Java/Go/Node 混布） | **强烈推荐**：统一治理，不必每个语言写一遍 SDK |
| 已有 Spring Cloud，纯 Java 单体微服务 | 可选：收益主要是灰度/可观测，但要付运维成本 |
| 流量低、治理简单 | 别用：Sidecar 增加内存（每个约几十 MB）与链路一跳延迟 |
| 已用 Nacos/Sentinel 且够用 | 不必迁移：Istio 不解决注册中心问题（仍需 Nacos 或 K8s DNS） |
| 追求统一灰度 + 安全 + 可观测的平台型团队 | 推荐：一次投入，长期解放业务代码 |

**成本提醒**：Sidecar 不是免费的——每个 Pod 多一个容器（内存开销、网络多一跳）、控制面本身要 HA 部署、排障从"看 Java 日志"变成"先看 Envoy 再进 Java"，团队要学习曲线。

## 50.8 练习与思考

1. **练习 A**：本地起 Kind 集群 + Istio，把第 31 章 mall-boot 改造为两个版本镜像（v1/v2），完成 90/10 金丝雀发布；
2. **练习 B**：配置故障注入（延迟 5s / 503），用压测验证超时、重试、熔断策略生效，并截图 Jaeger 链路；
3. **练习 C**：开启 mTLS（PeerAuthentication），用 `kubectl exec` 在集群内无证书访问验证被拒；
4. **练习 D**：为 order 服务写 AuthorizationPolicy，验证只有指定服务能调用；
5. **练习 E**：对比文档：你的服务从 Spring Cloud 治理迁移到 Istio，哪些能力下移了、哪些必须留在代码里？

## 50.9 面试考点

1. 服务网格解决什么问题？三代微服务治理演进（集中式/SDK/边车）？
2. Sidecar 模式是什么？如何做到业务无侵入（Webhook 注入）？
3. Istio 数据面/控制面架构？istiod 的 Pilot/Citadel/Galley 各做什么？
4. VirtualService / DestinationRule / Gateway 三者的关系？
5. 用 Istio 怎么做金丝雀发布（Header 灰度 + 权重灰度）？
6. 熔断、重试、超时在 Istio 里如何配置（对比代码方案）？
7. mTLS 的原理与价值？AuthorizationPolicy 怎么用？
8. Istio 为什么能"免费"提供可观测性？
9. Istio 与 Spring Cloud、Kong、Nacos 的边界？什么场景该用/不该用？
10. Sidecar 的成本是什么？团队引入 Istio 要付出什么代价？

---

至此，第 50 章 Service Mesh 与 Istio 学习完成。下一章 [51-Serverless.md](./51-Serverless.md)（连服务器都不用管的架构形态）｜ 返回：[README.md](./README.md)
