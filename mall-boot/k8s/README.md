# mall-boot K8s 部署清单

## 部署前提

- 已就绪的 K8s 集群（推荐 kind / minikube / 云厂商托管集群）
- 已安装 ingress-nginx
- 已构建应用镜像（含 SkyWalking 探针，见 `docker/Dockerfile`）

```bash
# 构建镜像（本地集群用 kind load 或 minikube image load 导入）
cd mall-boot
mvn clean package -DskipTests
docker build -f docker/Dockerfile -t mall-boot:1.0.0 .
kind load docker-image mall-boot:1.0.0   # minikube: minikube image load mall-boot:1.0.0
```

## 部署步骤

```bash
cd mall-boot

# 1. 命名空间
kubectl apply -f k8s/namespace.yaml

# 2. 初始化 SQL（mall.sql 以 ConfigMap 形式挂载进 MySQL 初始化目录）
kubectl create configmap mall-init-sql --from-file=sql/ -n mall-boot

# 3. 配置与密钥
kubectl apply -f k8s/configmap.yaml -f k8s/secret.yaml

# 4. 基础设施（MySQL / Redis）
kubectl apply -f k8s/mysql.yaml -f k8s/redis.yaml

# 5. 应用与入口
kubectl apply -f k8s/mall-boot.yaml -f k8s/ingress.yaml

# 查看状态
kubectl get pod,svc,sts,pvc -n mall-boot
kubectl logs -n mall-boot -l app=mall-boot -f
```

## 验证

```bash
# 1. 服务正常响应（健康检查即商品列表接口）
kubectl port-forward -n mall-boot svc/mall-boot-svc 8080:8080
curl http://localhost:8080/api/product/list

# 2. Redis 实际对接验证（新增的对接验证接口）
curl http://localhost:8080/api/redis/verify

# 3. 通过 Ingress 访问（需 hosts 映射）
curl http://mall.local/api/product/list
```

## 常用运维命令

```bash
kubectl scale deployment mall-boot --replicas=3 -n mall-boot   # 水平扩容
kubectl rollout restart deployment mall-boot -n mall-boot      # 滚动重启
kubectl delete configmap mall-init-sql -n mall-boot            # 清理（不影响已初始化数据）
```

## 文件清单

| 文件 | 说明 |
| --- | --- |
| `namespace.yaml` | 命名空间隔离 |
| `configmap.yaml` | 应用环境变量（非敏感） |
| `secret.yaml` | 数据库密码 / JWT 密钥 |
| `mysql.yaml` | MySQL 8 StatefulSet + PVC + Headless Service |
| `redis.yaml` | Redis 7 Deployment + PVC |
| `mall-boot.yaml` | 应用 Deployment（2 副本）+ Service |
| `ingress.yaml` | nginx Ingress 入口 |
