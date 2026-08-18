# mall-cloud Helm Chart

把 `k8s/` 下分散的 15 个清单收拢为一个可参数化的 Chart：一条命令部署
**MySQL + Redis + Nacos + Seata + Sentinel + SkyWalking + 4 个微服务 + Ingress**，
基础设施可独立开关，服务通过 `values.services` 增删。

## 快速开始

```bash
# 0. 构建四个服务镜像（使用通用 Dockerfile，见 docker/service/Dockerfile）
docker build -f docker/service/Dockerfile \
  --build-arg JAR_FILE=mall-gateway/target/mall-gateway.jar --build-arg SW_AGENT_NAME=mall-gateway \
  -t mall-gateway:1.0.0 .
# ... 依次构建 mall-user / mall-product / mall-order（tag 需与 values.global.image.tag 一致）

# 1. 安装（自动创建命名空间）
cd helm/mall-cloud
helm install mall-cloud . --namespace mall-cloud --create-namespace

# 2. 等待所有 Pod Ready（MySQL/Nacos 启动较慢）
kubectl -n mall-cloud get pod -w

# 3. 验证
curl -H "Host: mall.local" http://<集群IP>/product/list
curl -H "Host: mall.local" http://<集群IP>/order/sentinel/demo   # 连续刷新观察 429
```

## 关键参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `global.namespace` | `mall-cloud` | 命名空间 |
| `global.image.tag` | `1.0.0` | 四个服务镜像统一版本 |
| `secrets.*` | 演示默认值 | DB 口令 / MySQL root 口令（生产覆盖） |
| `infra.mysql.enabled` | `true` | 内置 MySQL（三库自动初始化 + undo_log） |
| `infra.redis.enabled` | `true` | 内置 Redis |
| `infra.nacos.enabled` | `true` | 内置 Nacos（8848/9848） |
| `infra.seata.enabled` | `true` | 内置 Seata Server（注册到集群内 Nacos） |
| `infra.sentinel.enabled` | `true` | Sentinel 控制台（8858） |
| `infra.skywalking.enabled` | `true` | OAP + UI（探针上报 11800） |
| `services.<gateway/user/product/order>.enabled` | `true` | 各服务开关 |
| `services.<svc>.replicas` | `2` | 各服务副本数 |
| `services.<svc>.dbName` | 按服务 | 覆盖各服务的库名（空则无数据源） |
| `services.<svc>.extraEnv` | `[]` | 追加环境变量（如 order 的 Seata 事务分组） |
| `ingress.enabled/host` | `true` / `mall.local` | Ingress 入口（指向网关） |

## 只部署应用、复用外部基础设施

```bash
helm upgrade mall-cloud . \
  --set infra.mysql.enabled=false \
  --set infra.redis.enabled=false \
  --set infra.nacos.enabled=false \
  --set infra.seata.enabled=false \
  --set infra.sentinel.enabled=false \
  --set infra.skywalking.enabled=false
```
> 此时需自行把共享 ConfigMap 中的 `NACOS_ADDR` 等改为外部地址：
> `kubectl -n mall-cloud edit configmap <release>-config` 后滚动重启服务。

## Sentinel 规则（配置中心化）

规则存放在 Nacos（group=`SENTINEL_GROUP`），服务启动自动拉取、修改实时推送：

```bash
# 本地端口转发后执行 docker/nacos-config/init-sentinel-rules.(sh|ps1)，或直接在控制台编辑
kubectl -n mall-cloud port-forward svc/<release>-nacos 8848:8848
bash ../../docker/nacos-config/init-sentinel-rules.sh   # NACOS_ADDR=localhost:8848
```

## 目录结构

```
mall-cloud/helm/mall-cloud/
├── Chart.yaml
├── values.yaml                 # 全部可配置项（基础设施开关 + 四服务参数）
├── files/                      # 三库初始化 SQL（.Files.Get 渲染进 init ConfigMap）
└── templates/
    ├── _helpers.tpl
    ├── configmap.yaml          # 共享环境变量（Nacos/Sentinel/OAP/DB/Redis）
    ├── secret.yaml             # 敏感配置
    ├── mysql.yaml / redis.yaml / nacos.yaml / seata.yaml / sentinel.yaml / skywalking.yaml
    ├── service.yaml            # 通用模板：range 遍历 services 生成 Deployment+Service
    ├── ingress.yaml
    └── NOTES.txt
```

## 与原生 k8s/ 清单的关系

- 部署对象一一对应；Helm 增加参数化：基础设施开关、副本数、镜像 tag、密钥外置。
- 服务模板 `service.yaml` 是 `app-*.yaml` 四个清单的通用化合并（差异用 `dbName`/`extraEnv` 表达）。
