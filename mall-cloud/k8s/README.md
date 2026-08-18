# mall-cloud K8s 部署清单

## 部署前提

- 就绪的 K8s 集群（kind / minikube / 托管集群均可）
- 已安装 ingress-nginx
- 已构建 4 个微服务镜像（通用 Dockerfile：`docker/service/Dockerfile`，内置 SkyWalking 探针）

```bash
cd mall-cloud
# 逐个构建（以 order 为例，其余同理）
mvn -pl mall-order -am clean package -DskipTests
docker build -f docker/service/Dockerfile \
    --build-arg JAR_FILE=mall-order/target/mall-order-1.0.0.jar \
    --build-arg SW_AGENT_NAME=mall-order \
    -t mall-order:1.0.0 .
# kind: kind load docker-image mall-order:1.0.0   （minikube: minikube image load）
```

## 部署步骤

```bash
cd mall-cloud

# 1. 命名空间
kubectl apply -f k8s/namespace.yaml

# 2. 初始化 SQL（三库脚本挂进 MySQL 初始化目录，自动建库建表 + Seata undo_log）
kubectl create configmap mall-init-sql --from-file=sql/ -n mall-cloud

# 3. 配置与密钥
kubectl apply -f k8s/configmap.yaml -f k8s/secret.yaml

# 4. 基础设施（顺序：MySQL → Redis → Nacos → Seata → Sentinel → SkyWalking）
kubectl apply -f k8s/infra-mysql.yaml -f k8s/infra-redis.yaml
kubectl apply -f k8s/infra-nacos.yaml
kubectl apply -f k8s/infra-seata.yaml
kubectl apply -f k8s/infra-sentinel.yaml -f k8s/infra-skywalking.yaml

# 5. 微服务（Nacos 就绪后再起，便于注册）
kubectl apply -f k8s/app-gateway.yaml -f k8s/app-user.yaml
kubectl apply -f k8s/app-product.yaml -f k8s/app-order.yaml
kubectl apply -f k8s/ingress.yaml

kubectl get pod,svc -n mall-cloud
```

## 验证

```bash
# 1. Nacos 控制台：http://<nodeIP>:8848/nacos 查看服务列表（默认 nacos/nacos）
kubectl port-forward -n mall-cloud svc/mall-nacos 8848:8848

# 2. 经网关访问（本地映射 hosts）
curl http://mall.local/product/list

# 3. Sentinel 限流演示（QPS 限制 2，多刷几次观察 429 兜底）
curl http://mall.local/order/sentinel/demo

# 4. Sentinel 控制台：http://<nodeIP>:8858 （sentinel/sentinel）
kubectl port-forward -n mall-cloud svc/mall-sentinel 8858:8858

# 5. SkyWalking UI：http://<nodeIP>:8080 （K8s 内 Service 端口）
kubectl port-forward -n mall-cloud svc/mall-skywalking-ui 8080:8080
```

## 文件清单

| 文件 | 说明 |
| --- | --- |
| `namespace.yaml` | 命名空间 |
| `configmap.yaml` | 共享环境变量（Nacos/Sentinel/OAP/数据源公共项） |
| `secret.yaml` | 数据库密码等敏感项 |
| `infra-mysql.yaml` | MySQL 8 StatefulSet（三库共实例） |
| `infra-redis.yaml` | Redis 7 Deployment + PVC |
| `infra-nacos.yaml` | Nacos Server 单机 |
| `infra-seata.yaml` | Seata Server（Nacos 注册 + file 存储） |
| `infra-sentinel.yaml` | Sentinel 控制台 |
| `infra-skywalking.yaml` | SkyWalking OAP + UI |
| `app-gateway.yaml` | 网关（唯一入口） |
| `app-user.yaml` | 用户服务 |
| `app-product.yaml` | 商品服务 |
| `app-order.yaml` | 订单服务 |
| `ingress.yaml` | nginx Ingress |
