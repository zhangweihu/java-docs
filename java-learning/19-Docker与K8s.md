# 第十九章 Docker 与 Kubernetes 容器化

> 本章目标：理解容器化解决什么问题，掌握 Docker 核心命令与 Dockerfile 编写，会把自己写的 Spring Boot 应用镜像化部署；理解 K8s 核心概念（Pod/Deployment/Service）与部署方式。
>
> 前置知识：第九章 Spring Boot 打包部署、第十五章微服务。前面章节大量用 Docker 启动中间件（Nacos、RabbitMQ 等），本章系统讲解。

## 19.1 为什么要容器化

### 19.1.1 "在我电脑上是好的啊"困境

传统部署的痛点：

| 问题 | 示例 |
| --- | --- |
| **环境不一致** | 开发 Windows、测试 CentOS、生产 Ubuntu，JDK 版本还不同 |
| **依赖冲突** | 两个项目一个要 MySQL 5.7，一个要 MySQL 8.0 |
| **部署繁琐** | 手动装 JDK、拷 jar、写启动脚本，重复劳动且易出错 |
| **资源隔离差** | 一个应用占满 CPU，拖垮同机器的其他应用 |
| **扩容困难** | 高并发时手动再部署一份，效率低 |

### 19.1.2 Docker 的解决思路：镜像 + 容器

```
┌────────────────────────────────────────────┐
│              Docker 宿主机                  │
│                                            │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐     │
│  │ 容器 1   │  │ 容器 2   │  │ 容器 3   │     │   ← 互相隔离的进程
│  │ 镜像A    │  │ 镜像B    │  │ 镜像C    │     │
│  └────┬────┘  └────┬────┘  └────┬────┘     │
│       │            │            │           │
│  ┌────┴────────────┴────────────┴────┐     │
│  │      Docker 引擎（守护进程）        │     │
│  └────┬──────────────────────────────┘     │
│       │                                    │
│  ┌────┴────┐  ┌──────────┐  ┌──────────┐   │
│  │ 操作系统  │  │ 硬件 CPU  │  │ 内存/磁盘  │   │
│  └─────────┘  └──────────┘  └──────────┘   │
└────────────────────────────────────────────┘
```

**关键概念**：

| 概念 | 类比 | 说明 |
| --- | --- | --- |
| **镜像 Image** | 安装光盘 / 类 | 只读模板，包含代码 + 运行环境（JDK、依赖、配置） |
| **容器 Container** | 装好的电脑 / 对象 | 镜像运行起来的实例，可启动、停止、删除 |
| **Dockerfile** | 安装说明书 | 描述如何构建镜像的脚本 |
| **仓库 Registry** | 软件商店 | 存放镜像的地方（Docker Hub / 阿里云） |
| **Docker 引擎** | 操作系统内核 | 负责管理容器的运行 |

> **镜像 = 类，容器 = 对象**。一个镜像可以启动多个容器，互不影响。
> 与虚拟机对比：虚拟机要装完整操作系统（GB 级、启动慢），容器**共享宿主机内核**（MB 级、秒级启动），性能损耗极小。

## 19.2 Docker 核心命令（必会）

### 19.2.1 镜像命令

```bash
# 拉取镜像（前面章节一直用）
docker pull mysql:8.0

# 查看本地镜像
docker images

# 删除镜像
docker rmi mysql:8.0

# 查看镜像详细信息
docker inspect mysql:8.0
```

### 19.2.2 容器命令（生命周期）

```bash
# 运行容器（-d 后台运行，-p 端口映射，--name 起名）
docker run -d --name my-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=123456 mysql:8.0

# 查看运行中的容器 / 全部容器
docker ps
docker ps -a

# 进入容器内部（调试神器）
docker exec -it my-mysql bash

# 查看容器日志（排障第一动作）
docker logs -f my-mysql

# 停止 / 启动 / 重启
docker stop my-mysql
docker start my-mysql
docker restart my-mysql

# 删除容器（-f 强制删除运行中的）
docker rm -f my-mysql

# 容器与宿主机拷贝文件
docker cp 宿主机文件 my-mysql:/tmp/
```

### 19.2.3 端口映射与数据卷

**端口映射**（`-p 宿主机端口:容器端口`）：

```
宿主机 3306 ──► 容器内 3306
访问 192.168.x.x:3306 就能连上容器里的 MySQL
```

**数据卷**（`-v`，容器删除数据不丢）：

```bash
# 把宿主机的 ./mysql-data 目录挂载到容器 /var/lib/mysql
docker run -d --name mysql \
  -v /d/data/mysql:/var/lib/mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 mysql:8.0
```

> **记住**：容器是"一次性"的（删了重跑），数据必须放数据卷里。

## 19.3 把 Spring Boot 应用做成镜像

### 19.3.1 准备一个可运行的 jar

回顾第九章：`mvn clean package` 得到 `springboot-demo-0.0.1-SNAPSHOT.jar`。

### 19.3.2 编写 Dockerfile

```dockerfile
# 1. 基础镜像：官方 OpenJDK 17
FROM openjdk:17-jdk-slim

# 2. 作者信息（可选）
LABEL maintainer="yourname@example.com"

# 3. 设置工作目录
WORKDIR /app

# 4. 拷贝 jar 包到容器（改名 app.jar）
COPY target/springboot-demo-0.0.1-SNAPSHOT.jar app.jar

# 5. 暴露端口（与 application.yml 的 server.port 一致）
EXPOSE 8080

# 6. 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**多阶段构建（生产最佳实践，镜像小、无源码）**：

```dockerfile
# 阶段一：构建（需要 Maven 环境）
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline          # 先缓存依赖
COPY src ./src
RUN mvn clean package -DskipTests

# 阶段二：运行（只要 JDK，镜像从几百 MB 降到 200MB 内）
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 19.3.3 构建并运行自己的镜像

```bash
# 1. 构建镜像（-t 起个名字，. 指当前目录的 Dockerfile）
docker build -t my-springboot:v1 .

# 2. 运行容器
docker run -d --name demo-app -p 8080:8080 my-springboot:v1

# 3. 验证
curl http://localhost:8080/hello

# 4. 部署多个实例（端口不冲突即可，模拟集群）
docker run -d --name demo-app2 -p 8081:8080 my-springboot:v1
```

### 19.3.4 Docker Compose：一键启动整套环境

本地开发时（MySQL + Redis + Nacos + 自己的应用）一条命令全启动。创建 `docker-compose.yml`：

```yaml
version: "3.8"

services:
  mysql:
    image: mysql:8.0
    container_name: mysql
    ports: ["3306:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: 123456
    volumes: ["./data/mysql:/var/lib/mysql"]

  redis:
    image: redis:7
    container_name: redis
    ports: ["6379:6379"]

  rabbitmq:
    image: rabbitmq:3.13-management
    container_name: rabbitmq
    ports: ["5672:5672", "15672:15672"]

  app:                          # 自己的 Spring Boot 应用
    build: .                   # 用当前目录 Dockerfile 构建
    container_name: my-app
    ports: ["8080:8080"]
    depends_on:                # 依赖关系：先启动中间件
      - mysql
      - redis
      - rabbitmq
```

```bash
docker-compose up -d      # 一键启动全部
docker-compose logs -f    # 看所有服务日志
docker-compose down       # 停止并删除
```

> 这是本地开发的**标准姿势**：一个 yml 文件管理整个环境。

## 19.4 Kubernetes（K8s）入门

### 19.4.1 单机 Docker 的局限与 K8s 的诞生

容器多了以后，又要处理新问题：

- 容器挂了谁来拉起？（自愈）
- 流量大了怎么自动扩容？（自动伸缩）
- 多台服务器如何统一调度？（集群管理）
- 滚动升级时怎么不中断服务？（发布策略）

**Kubernetes（K8s）**：Google 开源的**容器编排平台**，事实上的"容器操作系统"。它把多台服务器聚合成一个资源池，统一管理所有容器。

### 19.4.2 核心概念

```
                    ┌─────────────────────────────┐
                    │         K8s 集群             │
                    │                             │
        kubectl ──► │  Master（控制平面）           │
        (命令行)    │  · API Server：所有操作的入口  │
                    │  · Scheduler：决定容器放哪台  │
                    │  · Controller：维持期望状态   │
                    │                             │
                    ├─────────────┬───────────────┤
                    │ Node 1       │ Node 2        │  ← 工作节点（物理/虚拟机）
                    │ ┌─────────┐  │ ┌─────────┐   │
                    │ │ Pod     │  │ │ Pod     │   │  ← 最小部署单元（一个或多个容器）
                    │ │ 容器     │  │ │ 容器     │   │
                    │ └─────────┘  │ └─────────┘   │
                    │ ┌─────────┐  │ ┌─────────┐   │
                    │ │ Pod     │  │ │ Pod     │   │
                    │ └─────────┘  │ └─────────┘   │
                    └─────────────┴───────────────┘
```

| 概念 | 类比 | 说明 |
| --- | --- | --- |
| **Pod** | 豆荚/一盒容器 | K8s 最小调度单位，一个 Pod 里可有一个或多个容器（共享网络与存储） |
| **Deployment** | 部署控制器 | 声明"我要跑几个副本"，负责滚动更新、自愈 |
| **Service** | 稳定的入口 | Pod IP 会变，Service 提供固定访问地址 + 负载均衡 |
| **Ingress** | 网关 | 外部流量入口，按域名/路径转发到 Service |
| **ConfigMap/Secret** | 配置中心 | 把配置从镜像里剥离开 |
| **Namespace** | 命名空间 | 逻辑隔离（dev/test/prod） |

### 19.4.3 核心工作负载类型

| 类型 | 适用场景 | 类比 |
| --- | --- | --- |
| **Deployment** | 无状态服务（Web、API） | 标准部署，支持滚动更新 |
| **StatefulSet** | 有状态服务（MySQL、Redis、ZK） | 每个实例有固定身份与存储 |
| **DaemonSet** | 每台机器都要跑一个（日志采集） | 监控 Agent |
| **Job / CronJob** | 一次性任务 / 定时任务 | 对应 xxl-job 场景（K8s 也管） |

### 19.4.4 用 YAML 部署 Spring Boot 应用

**步骤 1：Deployment（部署）**

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo-app
  labels:
    app: demo-app
spec:
  replicas: 3                    # 运行 3 个副本（自动负载均衡 + 故障自愈）
  selector:
    matchLabels:
      app: demo-app
  template:
    metadata:
      labels:
        app: demo-app
    spec:
      containers:
        - name: demo-app
          image: my-springboot:v1        # 镜像
          ports:
            - containerPort: 8080
          env:                           # 环境变量注入配置
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
          resources:                     # 资源限制（核心！）
            requests: { cpu: "250m", memory: "512Mi" }
            limits: { cpu: "1", memory: "1Gi" }
          livenessProbe:                 # 存活探针：挂了自动重启
            httpGet: { path: /actuator/health, port: 8080 }
            initialDelaySeconds: 30
          readinessProbe:                # 就绪探针：好了才接入流量
            httpGet: { path: /actuator/health, port: 8080 }
```

**步骤 2：Service（服务入口）**

```yaml
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: demo-app-service
spec:
  selector:
    app: demo-app              # 关联到上面 Deployment 的 Pod
  ports:
    - port: 80                 # 集群内访问端口
      targetPort: 8080         # 转发到容器端口
  type: ClusterIP              # 集群内部访问（默认）
```

**步骤 3：Ingress（外部访问）**

```yaml
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: demo-ingress
spec:
  rules:
    - host: demo.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: demo-app-service
                port: { number: 80 }
```

**步骤 4：应用配置**

```bash
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
kubectl apply -f ingress.yaml

kubectl get pods            # 查看 Pod 状态（3 个 Running）
kubectl get svc             # 查看 Service
kubectl logs -f <pod名>      # 查看日志
kubectl scale deployment demo-app --replicas=5   # 手动扩容
kubectl rollout restart deployment demo-app      # 滚动重启（平滑更新）
```

> **核心思想**：K8s 是"**声明式**"的——你只描述"想要什么状态"（3 个副本），控制器自动把现状改成期望状态。Pod 挂了自动新建，永远保持 3 个。

### 19.4.5 与前面章节的串联

- 微服务（第十五章）上了 K8s 后：Nacos 负责服务发现，K8s 负责资源调度
- 镜像化后 CI/CD 才能自动化（第二十一章 Jenkins 就干这件事）
- 配置中心（Nacos Config）与 K8s ConfigMap 二选一即可，不用重复

## 19.5 小结与练习

**本章重点**：
- 镜像（类）/ 容器（对象）；共享内核，秒级启动
- 核心命令：`pull/run/ps/exec/logs/rm`，`-p` 端口映射、`-v` 数据卷
- Dockerfile：多阶段构建让镜像最小化
- Compose 一键启动整套开发环境
- K8s：**Pod + Deployment + Service + Ingress** 四件套 + 声明式自愈/扩容

**面试题参考**：
1. 容器和虚拟机的区别？
2. Docker 镜像分层原理？（联合文件系统，相同层复用）
3. Dockerfile 常用指令？（FROM/COPY/RUN/EXPOSE/ENTRYPOINT）
4. 容器数据为什么要用数据卷？
5. K8s 核心对象有哪些？Deployment 和 StatefulSet 区别？
6. Pod 挂了会怎样？（Deployment 控制器自动重建，保证副本数）

**课后练习**：
1. 用 Docker 分别启动 MySQL、Redis、RabbitMQ，并验证端口连通。
2. 把自己第九章的 Spring Boot 项目打成 jar，写 Dockerfile（多阶段构建）并运行。
3. 用 docker-compose 编排 MySQL + Redis + 自己的应用，一条命令启动整套环境。
4. 熟悉 `docker logs`、`docker exec`、`docker inspect` 三个排障命令。
5. 写出 K8s Deployment（3 副本）+ Service + 探针的 YAML，理解每行含义。

上一章：[18-XXLJob.md](./18-XXLJob.md) | 下一章：[20-Elasticsearch.md](./20-Elasticsearch.md) | 返回目录：[README.md](./README.md)
