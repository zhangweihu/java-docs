# =============================================================
# mall-boot 镜像构建文件（含 SkyWalking 探针接入）
# 构建：mvn clean package -DskipTests
#       docker build -f docker/Dockerfile -t mall-boot:1.0.0 .
# 运行：docker run -d -p 8080:8080 --env-file .env mall-boot:1.0.0
# 说明：
#   1. 分阶段构建（builder 只负责编译，runtime 只保留 JRE，镜像更小更安全）
#   2. SkyWalking Java Agent 一并打进镜像，默认通过 -javaagent 自动上报；
#      未启动 OAP 时仅打印连接告警，不影响业务启动
# =============================================================

# ---- 阶段一：编译 ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
# 先拷贝 pom 以利用 Docker 层缓存，依赖不变时不会重新下载
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- 阶段一.五：下载 SkyWalking Java Agent ----
FROM eclipse-temurin:17-jre AS agent
# 版本可从 https://archive.apache.org/dist/skywalking/java-agent/ 查询
ARG SW_AGENT_VERSION=9.1.0
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tar \
    && curl -fsSL -o /tmp/sw-agent.tar.gz \
       https://archive.apache.org/dist/skywalking/java-agent/${SW_AGENT_VERSION}/apache-skywalking-java-agent-${SW_AGENT_VERSION}.tar.gz \
    && mkdir -p /opt/sw-agent \
    && tar -xzf /tmp/sw-agent.tar.gz -C /opt/sw-agent --strip-components=1 \
    && rm -f /tmp/sw-agent.tar.gz \
    && apt-get purge -y curl tar && apt-get autoremove -y

# ---- 阶段二：运行（仅 JRE）----
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxMetaspaceSize=256m" \
    # SkyWalking 探针：服务名 + OAP 地址（生产用 k8s Service 名，如 mall-oap:11800）
    SW_AGENT_NAME=mall-boot \
    SW_AGENT_COLLECTOR_BACKEND_SERVICES=127.0.0.1:11800 \
    JAVA_TOOL_OPTIONS="-javaagent:/skywalking/agent/skywalking-agent.jar"
COPY --from=builder /build/target/mall-boot-1.0.0.jar app.jar
COPY --from=agent /opt/sw-agent /skywalking/agent
# 非 root 用户运行，降低容器逃逸风险
RUN useradd -m appuser
USER appuser
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
