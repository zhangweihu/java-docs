# 第三十五章 Docker 系统学习（从镜像容器到生产编排）

> 本章目标：系统掌握 Docker——核心概念、镜像与容器管理、数据卷、网络、Dockerfile 最佳实践、Compose 编排、私有仓库与 CI/CD 集成，最后衔接 Kubernetes。与第十九章《Docker 与 K8s》配套：19 章快速上手，本章深入运维与生产落地。
>
> 前置知识：第十九章 Docker 与 K8s（基础概念）、第九章 Spring Boot 打包部署。

## 35.1 容器化背景与核心概念

### 35.1.1 传统部署的四大痛点

| 痛点 | 表现 |
| --- | --- |
| 环境不一致 | 开发 Windows / 生产 Linux，JDK 版本不同 |
| 依赖冲突 | 两个项目各要 MySQL 5.7 / 8.0 |
| 部署繁琐 | 手动装 JDK、拷 jar、写脚本，易出错 |
| 资源隔离差 | 一个应用吃满 CPU 拖垮整机 |

### 35.1.2 Docker 与虚拟机对比

```
虚拟机：每个 VM 一个完整操作系统          Docker：所有容器共享宿主机内核
┌─────────┐ ┌─────────┐                  ┌───────┐ ┌───────┐
│ App     │ │ App     │                  │ App   │ │ App   │
│ Guest OS│ │ Guest OS│   GB 级、分钟启动 │ 依赖库 │ │ 依赖库 │
├─────────┴─┴─────────┤                  ├───────┴─┴───────┤
│  Hypervisor（虚拟化层）│                  │  Docker Engine  │
├─────────────────────┤                  ├─────────────────┤
│     宿主操作系统       │                  │   宿主操作系统    │
└─────────────────────┘                  └─────────────────┘
 隔离强、开销大、启动慢                     隔离弱些、MB 级、秒级启动
```

### 35.1.3 核心概念（务必分清）

| 概念 | 类比 | 说明 |
| --- | --- | --- |
| **镜像 Image** | 安装光盘 / 类 | 只读模板，分层存储 |
| **容器 Container** | 运行中的进程 / 对象 | 镜像运行实例，可启停删 |
| **仓库 Registry** | 软件商店 | 存镜像（Docker Hub / 阿里云 / Harbor） |
| **Dockerfile** | 安装说明书 | 构建镜像的脚本 |
| **守护进程 dockerd** | 引擎 | 管理镜像/容器/网络/存储 |
| **客户端 docker** | 遥控器 | 命令行与守护进程交互 |

> **镜像 = 类，容器 = 对象**；`docker run` 从镜像创建容器。一个镜像可启动多个容器，互不影响。

## 35.2 安装与镜像加速

### 35.2.1 各平台安装

```bash
# CentOS / Ubuntu
curl -fsSL https://get.docker.com | bash -s docker
sudo systemctl enable --now docker

# Windows / macOS
# 安装 Docker Desktop（WSL2 后端），官网下载安装包
```

### 35.2.2 镜像加速（国内必配）

```bash
# 编辑 /etc/docker/daemon.json
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["https://docker.m.daocloud.io", "https://hub-mirror.c.163.com"]
}
EOF
sudo systemctl restart docker
```

### 35.2.3 验证

```bash
docker version                    # 客户端 + 服务端版本
docker run hello-world            # 拉取并运行，输出 Hello from Docker!
```

## 35.3 镜像管理

### 35.3.1 镜像命令全解

```bash
docker search redis               # 搜索镜像
docker pull redis:7               # 拉取（默认 latest）
docker images                     # 查看本地镜像列表
docker image inspect redis:7      # 查看镜像详情（分层、配置）
docker tag redis:7 myredis:v7     # 打标签（重命名）
docker rmi myredis:v7             # 删除镜像（无容器引用时）
docker rmi $(docker images -q)    # 批量删除全部（慎用）

# 离线传输：save/load（内网环境常用）
docker save -o redis.tar redis:7
docker load -i redis.tar

# 推送到仓库（先登录）
docker login registry.example.com
docker tag mall-boot:1.0.0 registry.example.com/mall/mall-boot:1.0.0
docker push registry.example.com/mall/mall-boot:1.0.0
```

### 35.3.2 镜像分层与 UnionFS

```
镜像由多层只读层叠加（每层 = Dockerfile 一条指令的结果）
┌────────────────────────────────────────┐
│ 可写层（容器运行时写入，删除容器即丢失）      │
├────────────────────────────────────────┤
│ 第 3 层 COPY app.jar /app/            │
│ 第 2 层 RUN yum install -y java17     │
│ 第 1 层 FROM centos:7                 │
└────────────────────────────────────────┘
```

> **为什么分层**：复用公共层（不同镜像共享基础层，省磁盘）、增量拉取（只拉新层）、缓存构建（层未变跳过）。改动越靠后越好，基础层尽量不动。

## 35.4 容器管理

### 35.4.1 运行与生命周期

```bash
# 前台运行（-it 交互 + 终端）
docker run -it --name ubuntu1 ubuntu:22.04 bash

# 后台运行（-d 守护模式）
docker run -d --name myredis -p 6379:6379 redis:7

# 常用运行参数
docker run -d \
  --name mall-app \
  -p 8080:8080 \                        # 端口映射：宿主机:容器
  -e SPRING_PROFILES_ACTIVE=prod \      # 环境变量
  -v /opt/app/logs:/app/logs \          # 数据卷挂载
  --memory 512m --cpus 1 \              # 资源限制
  --restart unless-stopped \            # 宕机自动重启
  mall-boot:1.0.0

# 生命周期管理
docker ps                              # 运行中容器
docker ps -a                           # 全部（含已停止）
docker start myredis                   # 启动已停止的
docker stop myredis                    # 停止（优雅，等 10s）
docker restart myredis                 # 重启
docker rm myredis                      # 删除容器
docker rm -f myredis                   # 强制删除运行中的
docker rm $(docker ps -aq)             # 清理全部（慎用）
```

### 35.4.2 进入与日志

```bash
docker exec -it myredis bash          # 进入运行中的容器（生产排障必备）
docker logs -f myredis                # 跟踪日志（-f 实时）
docker logs --tail 100 myredis        # 最近 100 行
docker cp app.jar myredis:/app/       # 文件拷贝进/出容器
docker top myredis                    # 容器内进程
docker inspect myredis                 # 容器详细信息（IP、挂载、环境变量）
docker stats                          # 实时资源监控（CPU/内存/网络）
```

> **容器内无 `vim/ps` 等命令**属正常（最小化镜像）。排障顺序：`docker logs` → `docker exec` 看进程/配置 → 宿主机 `docker inspect`。

## 35.5 数据卷（持久化）

容器删除后数据丢失（可写层随容器销毁），数据必须放卷/挂载中。

### 35.5.1 三种方式

| 方式 | 说明 | 场景 |
| --- | --- | --- |
| **Volume 卷**（推荐） | Docker 管理，`docker volume` 操作 | 数据库数据、应用数据 |
| **Bind Mount** | 挂载宿主机目录 | 配置、开发热更新 |
| tmpfs | 内存挂载，不落盘 | 临时缓存 |

```bash
# 方式一：命名卷（数据由 Docker 管理，位于 /var/lib/docker/volumes/）
docker volume create mysql-data
docker run -d --name mysql8 \
  -v mysql-data:/var/lib/mysql \
  -e MYSQL_ROOT_PASSWORD=root123 \
  mysql:8.0
# 删容器后数据还在；docker volume rm 才删除

# 方式二：Bind Mount（宿主机目录直接映射）
docker run -d --name nginx \
  -v /opt/nginx/conf:/etc/nginx/conf.d \
  -v /opt/nginx/html:/usr/share/nginx/html \
  nginx:1.24
```

### 35.5.2 容器间共享

```bash
# volumes-from：容器 B 复用容器 A 的卷（旧式，已不推荐）
docker run -d --name app-a -v app-data:/data app-a-image
docker run -d --name app-b --volumes-from app-a app-b-image
```

> **生产铁律**：有状态服务（MySQL、Redis、ES）必须挂 Volume/Bind Mount，否则重启/升级即丢数据。项目里 `docker-compose.yml` 的 MySQL 均如此配置。

## 35.6 网络

### 35.6.1 默认网络模式

| 模式 | 说明 |
| --- | --- |
| bridge（默认） | 容器经虚拟网桥互联，可映射端口 |
| host | 容器直接用宿主机网络（无隔离，性能最好） |
| none | 无网络 |

### 35.6.2 自定义网络（生产推荐）

```bash
# 创建自定义桥接网络
docker network create mall-net

# 同网络内容器互相用"容器名"通信（内置 DNS，无需记 IP）
docker run -d --name mysql8 --network mall-net -e MYSQL_ROOT_PASSWORD=root123 mysql:8.0
docker run -d --name mall-app --network mall-net \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://mysql8:3306/mall \
  mall-boot:1.0.0
```

```bash
docker network ls                       # 查看网络
docker network inspect mall-net         # 查看容器与 IP
docker network connect mall-net myredis # 运行中动态加入网络
```

> **为什么用自定义网络**：默认 bridge 的 `--link` 已废弃；自定义网络提供**内置 DNS 解析**（容器名即域名），微服务/Compose 内部通信全靠它。`jdbc:mysql://mysql8:3306` 就是"容器名代替 IP"。

## 35.7 Dockerfile 详解（重点）

### 35.7.1 核心指令

| 指令 | 作用 | 示例 |
| --- | --- | --- |
| FROM | 基础镜像（必须第一行） | `FROM eclipse-temurin:17-jre` |
| LABEL | 元数据 | `LABEL maintainer="dev@mall.com"` |
| ENV | 环境变量 | `ENV JAVA_OPTS="-Xms256m -Xmx256m"` |
| ARG | 构建参数 | `ARG JAR_FILE=target/app.jar` |
| WORKDIR | 工作目录 | `WORKDIR /app` |
| COPY | 拷贝进镜像 | `COPY ${JAR_FILE} app.jar` |
| ADD | 拷贝+自动解压（慎用） | `ADD app.tar.gz /app/` |
| RUN | 构建时执行命令 | `RUN yum install -y curl` |
| EXPOSE | 声明端口（文档性质） | `EXPOSE 8080` |
| CMD | 容器启动命令（可被覆盖） | `CMD ["java","-jar","app.jar"]` |
| ENTRYPOINT | 容器启动入口（不可覆盖） | `ENTRYPOINT ["java","-jar","app.jar"]` |
| VOLUME | 声明挂载点 | `VOLUME /app/logs` |
| USER | 指定运行用户（安全） | `USER 1001:1001` |
| HEALTHCHECK | 健康检查 | `HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health` |

### 35.7.2 CMD vs ENTRYPOINT（易混）

| 指令 | 语义 | 可被 `docker run 参数` 覆盖 |
| --- | --- | --- |
| CMD | 提供默认启动命令 | 可以，整体替换 |
| ENTRYPOINT | 固定启动入口 | 不可以（拼接参数） |

```dockerfile
# 推荐组合：ENTRYPOINT 定入口 + CMD 定默认参数
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--spring.profiles.active=prod"]
# docker run image --spring.profiles.active=dev  → 追加到 java -jar app.jar 后面
```

### 35.7.3 多阶段构建（生产标准做法）

一个 `Dockerfile` 两阶段：构建环境出 jar，运行环境只留 JRE，镜像瘦身 70%+。

```dockerfile
# ========== 阶段一：Maven 构建 ==========
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B          # 先拉依赖（利用层缓存）
COPY src ./src
RUN mvn package -DskipTests -B

# ========== 阶段二：运行（最小化） ==========
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx256m"
EXPOSE 8080
USER 1001:1001                            # 非 root 运行（安全）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 35.7.4 构建优化技巧

| 技巧 | 说明 |
| --- | --- |
| **利用层缓存** | 变化小的放前面（pom.xml 在源码前），未变层直接复用 |
| **合并 RUN** | `RUN a && b && c` 减少层数与镜像大小 |
| **.dockerignore** | 排除 target/.git/node_modules，减小构建上下文 |
| 选对基础镜像 | JRE 而非 JDK、alpine 更小（注意 glibc 兼容） |
| 固定版本 tag | 不用 latest，保证可复现 |

```bash
docker build -t mall-boot:1.0.0 .        # 构建
docker build -t mall-boot:1.0.0 --build-arg JAR_FILE=target/mall.jar .  # 传 ARG
docker history mall-boot:1.0.0           # 查看分层与每条指令产物
```

## 35.8 Docker Compose（多容器编排）

### 35.8.1 为什么需要 Compose

单机多容器（应用 + MySQL + Redis + Nacos）逐个 `docker run` 太繁琐，Compose 用 `docker-compose.yml` 声明式描述，一条命令拉起/销毁。

### 35.8.2 语法详解

```yaml
version: "3.8"                    # 版本（新版可不写）

services:
  mysql8:
    image: mysql:8.0
    container_name: mall-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: root123
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql          # 数据卷持久化
      - ./sql:/docker-entrypoint-initdb.d  # 首次启动自动执行建表 SQL
    healthcheck:                           # 健康检查（依赖它的服务等它就绪）
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 5s
      timeout: 3s
      retries: 10

  app:
    build: .                                # 本地 Dockerfile 构建
    # image: mall-boot:1.0.0               # 或用现成镜像
    depends_on:
      mysql8:
        condition: service_healthy          # 等 MySQL 健康后再启动
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql8:3306/mall
      SPRING_DATA_REDIS_HOST: redis7
    ports:
      - "8080:8080"
    networks:
      - mall-net
    env_file: .env                          # 敏感配置放 .env（不提交 git）

  redis7:
    image: redis:7
    command: redis-server --appendonly yes
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

volumes:
  mysql-data:
  redis-data:

networks:
  mall-net:
```

### 35.8.3 常用命令

```bash
docker compose up -d              # 启动（后台）
docker compose up -d --build      # 重新构建并启动（代码变更后）
docker compose ps                 # 查看状态
docker compose logs -f app        # 查看某服务日志
docker compose exec app bash      # 进入某服务容器
docker compose down               # 停止并删除容器/网络（卷默认保留）
docker compose down -v            # 连同数据卷一起删（慎用！）
docker compose config             # 校验语法并输出渲染结果
docker compose up -d --scale app=3   # 服务扩容到 3 副本（仅无状态服务）
```

> 项目实战：mall-boot 的 `docker/docker-compose.yml`（应用+MySQL+Redis+SkyWalking）、mall-cloud 的编排（Nacos/Seata/Sentinel 等）均为 Compose 最佳范例，可对照学习。

## 35.9 私有仓库

### 35.9.1 搭建 registry（轻量）

```bash
docker run -d --name registry -p 5000:5000 \
  -v /opt/registry:/var/lib/registry \
  --restart unless-stopped \
  registry:2

# 推送流程
docker tag mall-boot:1.0.0 localhost:5000/mall-boot:1.0.0
docker push localhost:5000/mall-boot:1.0.0
```

### 35.9.2 Harbor（企业级，推荐）

Harbor 提供：Web UI、RBAC 权限、镜像扫描、复制、审计。

```bash
# 官方离线安装包（harbor-offline-installer-v2.x.tgz）
./install.sh                       # 默认 80 端口，配置见 harbor.yml
# 浏览器 http://harbor.example.com  → 项目 → 推送拉取
```

> 生产环境**镜像统一走 Harbor**：CI 构建后 push，K8s/服务器 pull，支持回滚（打版本 tag）、审计追踪。

## 35.10 资源限制与监控

### 35.10.1 资源限制（cgroup 实现）

```bash
docker run -d --name mall-app \
  --memory 512m \              # 最大内存 512MB
  --memory-swap 768m \         # 内存+swap 上限
  --cpus 1.5 \                 # 最多 1.5 核 CPU
  --pids-limit 512 \           # 进程数上限
  mall-boot:1.0.0
```

> Java 容器**必配内存上限**且开启容器感知：`-XX:MaxRAMPercentage=75`（或 Java 10+ 默认感知 cgroup），否则 JVM 按宿主机内存分配堆，容器 OOM 被杀。

### 35.10.2 监控命令

```bash
docker stats                        # 实时 CPU/内存/IO 监控
docker top mall-app                 # 容器内进程
docker events                       # 事件流（创建/销毁/错误）
docker system df                    # 磁盘占用（镜像/容器/卷/缓存）
docker system prune -f              # 清理悬空镜像/停止容器/构建缓存（慎用）
```

## 35.11 安全最佳实践

| 实践 | 说明 |
| --- | --- |
| **非 root 运行** | `USER 1001`，避免容器被攻破后拿到宿主 root |
| 最小镜像 | 少装包，减小攻击面 |
| 镜像扫描 | Trivy / Harbor 扫描漏洞：`trivy image mall-boot:1.0.0` |
| 密钥不入镜像 | 密码走环境变量/Secret（`docker secret`、K8s Secret），不写进 Dockerfile |
| 只读根文件系统 | `--read-only` + 挂载可写目录 |
| 网络隔离 | 最小暴露端口，内部网络不映射公网 |
| 固定版本 | 不用 `latest`，锁版本可审计、可回滚 |

```bash
# 一键扫描
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy image mall-boot:1.0.0
```

## 35.12 集成 CI/CD

### 35.12.1 Docker 在流水线中的角色

```
代码提交 → 单元测试 → 构建镜像 → 推送 Harbor → 部署（SSH / K8s）→ 健康检查
                     └── 全部在 Docker/容器中执行，环境一致
```

### 35.12.2 GitLab CI 示例

```yaml
stages: [build, deploy]

build:
  stage: build
  image: docker:24
  services: [docker:24-dind]                  # Docker-in-Docker
  script:
    - docker build -t ${REGISTRY}/mall-boot:${CI_COMMIT_SHORT_SHA} .
    - docker push ${REGISTRY}/mall-boot:${CI_COMMIT_SHORT_SHA}

deploy:
  stage: deploy
  script:
    - ssh deploy@server "docker pull ${REGISTRY}/mall-boot:${CI_COMMIT_SHORT_SHA} && docker compose up -d"
  environment: production
```

> 配套学习：第二十一章《Jenkins CI/CD》完整流水线；本项目 CI 亦可直接用 GitHub Actions / GitLab CI + Harbor。

## 35.13 从 Docker 到 Kubernetes

### 35.13.1 Docker 的局限（单机）

| 局限 | 表现 |
| --- | --- |
| 单机编排 | 无法跨多台机器调度 |
| 无自动扩容 | 流量高峰需手动起容器 |
| 无自愈 | 容器挂了不会自动重建到别的机器 |
| 无服务发现 | 端口/地址需要人工管理 |
| 无滚动发布 | 升级需要停服或手工操作 |

### 35.13.2 K8s 补齐的能力

| K8s 对象 | 对应 Docker 概念 | 解决 |
| --- | --- | --- |
| Pod | 容器（最小调度单元） | 一组容器的调度与网络 |
| Deployment | docker run | 副本数、滚动更新、自愈 |
| Service | 容器互联 | 稳定访问入口 + 负载均衡 |
| Ingress | 端口映射 | 域名/路径统一入口 |
| ConfigMap/Secret | 环境变量/挂载 | 配置与密钥管理 |
| HPA | docker stats | 按指标自动扩缩容 |
| Volume | Volume | 跨节点持久化存储 |

### 35.13.3 与第 19 章、Helm 的衔接

```
单容器 docker run → 多容器 docker compose → 集群调度 Kubernetes → Helm 打包发布
  35.4            35.8                    19 章 / K8s         项目 helm/ 目录
```

> 本项目 `mall-boot/k8s/`、`mall-cloud/k8s/` 是原生清单，`helm/` 是参数化打包（一键 `helm install`），三者对照学习可打通"容器 → 编排 → 发布"全链路。

## 35.14 练习与总结

### 练习题（动手实操）

```bash
# 1. 用多阶段构建做一个 Spring Boot 镜像，对比单阶段体积差异（docker images 看 SIZE）
# 2. 部署 MySQL + Redis + 应用三个容器到自定义网络，用容器名互联访问
# 3. 用 Compose 一键拉起"应用+MySQL"，重启 MySQL 容器验证数据不丢
# 4. 搭建 registry，push 一个镜像再 pull 回来验证
# 5. 给应用容器加 --memory 256m，压测触发 OOM，观察 docker stats 与日志
# 6. 用 trivy 扫描自己的镜像，修复 HIGH 级漏洞
```

### 面试高频问题清单

| 问题 | 答案要点 |
| --- | --- |
| Docker 与虚拟机区别？ | 共享内核 vs 独立 OS；MB/秒级 vs GB/分钟级 |
| 镜像分层原理？ | UnionFS 叠加只读层 + 可写层，复用缓存 |
| CMD 与 ENTRYPOINT 区别？ | 默认参数可覆盖 vs 固定入口不可覆盖 |
| 多阶段构建好处？ | 构建与运行分离，镜像最小化 |
| 数据持久化方式？ | Volume（推荐）/ Bind Mount / tmpfs |
| 容器间通信？ | 自定义网络 + 容器名 DNS |
| Compose 的 depends_on？ | 启动顺序 + condition 健康检查 |
| 镜像瘦身技巧？ | JRE、多阶段、合并 RUN、.dockerignore |
| 为什么需要 K8s？ | 多机调度、自动扩容、自愈、滚动发布 |

### 本章小结

- **基础**（35.1~35.4）：概念、安装加速、镜像/容器命令全解；
- **持久化与网络**（35.5~35.6）：Volume/Bind Mount、自定义网络 DNS；
- **镜像构建**（35.7）：Dockerfile 指令、多阶段构建、优化技巧；
- **生产落地**（35.8~35.12）：Compose 编排、私有仓库 Harbor、资源限制与监控、安全实践、CI/CD；
- **架构衔接**（35.13）：Docker → K8s → Helm 演进路线。

配套：第 19 章（Docker 与 K8s 入门）、第 21 章（Jenkins CI/CD）、第 31/32 章（项目容器化部署）、各项目 `docker/`、`helm/` 目录实操。

---

至此，三份专项学习手册（33-MySQL、34-Redis、35-Docker）完成。返回：[README.md](./README.md)
