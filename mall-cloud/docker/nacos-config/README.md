# Sentinel 规则配置中心化（Nacos 数据源）

将 Sentinel 流控规则统一存放在 **Nacos 配置中心**，业务服务启动时自动拉取，运行期通过
Nacos 监听实时推送——**改规则不用重启应用**，多个实例规则天然一致。

## 架构

```
                 ┌──────────────────────────────┐
                 │       Nacos 配置中心          │
                 │  dataId (group=SENTINEL_GROUP)│
                 │  ├─ mall-order-flow-rules     │
                 │  ├─ mall-product-flow-rules   │
                 │  ├─ mall-gateway-flow-rules   │
                 │  └─ mall-gateway-api-group-   │
                 │     rules                     │
                 └──────────┬───────────────────┘
                            │ 启动拉取 + 监听推送
        ┌───────────────┬───┴───┬───────────────┐
        ▼               ▼       ▼               ▼
   mall-gateway    mall-order  mall-product   (规则变更实时生效)
   (gw-flow)      (flow)      (flow)
```

- 服务侧：`spring.cloud.sentinel.datasource.*.nacos.*`（见各服务 `application.yml`），依赖
  `com.alibaba.csp:sentinel-datasource-nacos`（版本由 spring-cloud-alibaba BOM 管理）。
- 规则侧：JSON 文件（本目录）→ 脚本/控制台发布到 Nacos → 服务监听生效。

## 规则清单

| dataId | 类型 | 内容 |
| --- | --- | --- |
| `mall-order-flow-rules` | 流控(flow) | order:create=5QPS、order:detail=20QPS、sentinel:demo=2QPS |
| `mall-product-flow-rules` | 流控(flow) | product:detail=10QPS、product:list=20QPS |
| `mall-gateway-flow-rules` | 网关流控(gw-flow) | 路由 order-route=5QPS、API mall-product-api=20QPS |
| `mall-gateway-api-group-rules` | 网关分组(gw-api-group) | mall-product-api → 前缀匹配 `/product/**` |

> group 统一为 `SENTINEL_GROUP`，与各服务 `application.yml` 中 `group-id` 一致。

## 使用步骤

```bash
# 1. 先启动基础设施（Nacos 就绪）
cd mall-cloud/docker
docker compose up -d nacos

# 2. 发布规则（二选一，幂等可重复执行）
cd nacos-config
bash init-sentinel-rules.sh                 # Windows: .\init-sentinel-rules.ps1

# 3. 启动业务服务（或直接使用 IDE），启动日志出现
#    "SentinelDataSource ... 从 Nacos 拉取流控规则" 即表示加载成功
```

## 验证与动态调优

1. **验证加载**：`curl http://localhost:8080/order/sentinel/demo` 连续快速刷新，
   超过 2QPS 后返回 429 兜底 JSON。
2. **动态调优**（不改代码、不重启）：
   - 方式 A：Nacos 控制台 `http://localhost:8848/nacos` → 配置管理 → 配置列表，
     搜索 `mall-order-flow-rules`，把 `sentinel:demo` 的 `count` 改为 `1`，发布。
   - 方式 B：改完 JSON 后重新执行 `bash init-sentinel-rules.sh`。
   - 数秒内即生效，Sentinel 控制台 `http://localhost:8858`（sentinel/sentinel）可观察 QPS 曲线。
3. **回滚**：改回 `count` 再发布一次即可，全程无需重启服务。

## 与旧方案（本地代码规则）的差异

- 旧：规则写死在 `SentinelFlowRuleConfig`，改规则要改代码重新发版，多实例各自为政。
- 新：规则集中在 Nacos，一处修改全局生效；本地不再有任何程序化规则（已删除对应类）。

## 生产建议

- Nacos 开启鉴权（`NACOS_AUTH_ENABLE=true`），规则归属独立命名空间/分组管理。
- 规则版本化：Nacos 支持配置历史与回滚（控制台「更多-历史版本」）。
- 与 CI/CD 集成：流水线中调用 `init-sentinel-rules.sh`，规则变更走评审发布。
