# mall-boot Helm Chart

将 `k8s/` 下分散的 YAML 清单收拢为一个可参数化的 Chart：一个命令完成
**MySQL + Redis + 应用 + Ingress** 的全套部署，支持按环境覆盖配置。

## 快速开始

```bash
# 0. 构建镜像（若镜像已推送到仓库可跳过）
docker build -f docker/Dockerfile -t mall-boot:1.0.0 .

# 1. 安装（自动创建命名空间）
cd helm/mall-boot
helm install mall-boot . --namespace mall-boot --create-namespace

# 2. 验证
kubectl -n mall-boot get pod -w
curl -H "Host: mall.local" http://<集群IP>/api/product/list
curl -H "Host: mall.local" http://<集群IP>/api/redis/verify
```

## 关键参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `global.namespace` | `mall-boot` | 部署命名空间 |
| `image.repository/tag` | `mall-boot` / `1.0.0` | 应用镜像 |
| `app.replicas` | `2` | 副本数 |
| `app.profile` | `prod` | 激活 `application-prod.yml`（敏感项走环境变量） |
| `database.host/port/name` | `{{ .Release.Name }}-mysql` / `3306` / `mall` | 数据库连接（host 支持模板） |
| `redis.host/port` | `{{ .Release.Name }}-redis` / `6379` | Redis 连接 |
| `secrets.*` | 演示默认值 | DB 口令 / MySQL root 口令 / JWT 密钥（生产必须覆盖） |
| `mysql.enabled` | `true` | 是否内置 MySQL（StatefulSet + 自动建表） |
| `redis.enabled` | `true` | 是否内置 Redis |
| `ingress.enabled/host` | `true` / `mall.local` | Ingress 入口 |

## 使用外部基础设施

已有 MySQL/Redis 时关闭内置组件，并覆盖连接信息：

```bash
helm upgrade mall-boot . \
  --set mysql.enabled=false \
  --set redis.enabled=false \
  --set database.host=my-db.example.com \
  --set database.port=3306 \
  --set redis.host=my-redis.example.com \
  --set secrets.dbPassword=MyRealPass \
  --set secrets.jwtSecret=MyRealJwtSecretAtLeast32Bytes!!
```

## 目录结构

```
mall-boot/helm/mall-boot/
├── Chart.yaml          # Chart 元数据
├── values.yaml         # 全部可配置项（含注释）
├── files/mall.sql      # MySQL 初始化脚本（模板 .Files.Get 引用）
└── templates/
    ├── _helpers.tpl    # 命名/label 约定
    ├── configmap.yaml  # 非敏感环境变量
    ├── secret.yaml     # 敏感配置（b64）
    ├── deployment.yaml # 应用 Deployment（探针 + envFrom）
    ├── service.yaml    # 应用 Service
    ├── mysql.yaml      # 可选 MySQL StatefulSet（init SQL 自动挂载）
    ├── redis.yaml      # 可选 Redis Deployment + PVC
    ├── ingress.yaml    # Ingress
    └── NOTES.txt       # 安装后提示
```

## 与原生 k8s/ 清单的关系

- 两者部署的对象一致；Helm 增加了参数化（副本数、镜像、密钥、开关基础设施）。
- 迁移已用 `kubectl apply` 部署的旧资源：先 `helm install` 前删除同名资源避免冲突，
  或直接 `helm upgrade --install mall-boot .`（采用新资源）。
