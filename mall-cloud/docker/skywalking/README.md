# SkyWalking 探针接入指南（mall-cloud）

## 一、启动 OAP 后端 + UI

```bash
cd mall-cloud
docker compose -f docker/skywalking/docker-compose.yml up -d
```

- UI：http://localhost:8086 （登录无需账号，默认进入仪表盘）
- 探针上报端口：`11800`（gRPC）
- 存储默认 H2（演示）；生产改 `SW_STORAGE: elasticsearch` 并挂载持久化

## 二、为微服务接入探针（三种方式任选）

### 方式 A：IDE 本地启动（推荐开发调试）

在 IDEA/Eclipse 的运行配置 `VM options` 中加入（以 mall-order 为例）：

```
-javaagent:D:\skywalking-agent\skywalking-agent.jar
-Dskywalking.agent.service_name=mall-order
-Dskywalking.collector.backend_service=127.0.0.1:11800
```

> 本地开发机需先下载 Java Agent：
> `https://archive.apache.org/dist/skywalking/java-agent/9.1.0/apache-skywalking-java-agent-9.1.0.tar.gz`
> 解压后路径即 `D:\skywalking-agent\skywalking-agent.jar`。

### 方式 B：Docker 镜像（生产/测试）

使用本项目通用 Dockerfile（已内置探针）：

```bash
mvn -pl mall-order -am clean package -DskipTests
docker build -f docker/service/Dockerfile \
    --build-arg JAR_FILE=mall-order/target/mall-order-1.0.0.jar \
    --build-arg SW_AGENT_NAME=mall-order \
    -t mall-order:1.0.0 .
docker run -d -p 8083:8083 -e SW_AGENT_COLLECTOR_BACKEND_SERVICES=host.docker.internal:11800 mall-order:1.0.0
```

### 方式 C：K8s 部署

镜像构建同上，部署清单中通过 env 注入：

```yaml
env:
  - name: SW_AGENT_COLLECTOR_BACKEND_SERVICES
    value: mall-oap:11800   # SkyWalking OAP 的 K8s Service 名
```

## 三、验证是否接入成功

1. 调用任意业务接口产生流量，例如：

```bash
curl http://localhost:8083/order/detail/1
curl http://localhost:8082/product/detail/1
curl http://localhost:8081/user/1
```

2. 打开 UI http://localhost:8086 → 左侧「General Service」中选择服务（如 `mall-order`）。
3. 应能看到：调用链路（Trace）、服务拓扑、端点 QPS/RT/P99、JVM 指标。
4. 日志验证：启动日志出现 `SkyWalking agent` 相关行；若连接失败会打印 `Cannot connect to OAP server` 告警。

## 四、常用探针配置项

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `SW_AGENT_NAME` / `-Dskywalking.agent.service_name` | 服务在 UI 中的显示名 | 自动探测 |
| `SW_AGENT_COLLECTOR_BACKEND_SERVICES` | OAP 地址（逗号分隔多个） | 127.0.0.1:11800 |
| `SW_AGENT_LOG_LEVEL` | 探针日志级别 | INFO |
| `SW_AGENT_SAMPLE_N_PER_3_SECS` | 采样率（每 3 秒采样条数） | -1 全量 |

更多配置见官方文档：https://skywalking.apache.org/docs/skywalking-java/latest/
