# =============================================================
# mall-cloud 微服务通用镜像（含 SkyWalking 探针）
#
# 构建（以 mall-order 为例）：
#   cd mall-cloud
#   mvn -pl mall-order -am clean package -DskipTests
#   docker build -f docker/service/Dockerfile \
#       --build-arg JAR_FILE=mall-order/target/mall-order-1.0.0.jar \
#       --build-arg SW_AGENT_NAME=mall-order \
#       -t mall-order:1.0.0 .
#
# 运行：
#   docker run -d -p 8083:8083 --env-file .env mall-order:1.0.0
#   通过 -e SW_AGENT_COLLECTOR_BACKEND_SERVICES=host.docker.internal:11800 指定 OAP
# =============================================================

# ---- 阶段一：SkyWalking Java Agent ----
FROM eclipse-temurin:17-jre AS agent
ARG SW_AGENT_VERSION=9.1.0
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tar \
    && curl -fsSL -o /tmp/sw-agent.tar.gz \
       https://archive.apache.org/dist/skywalking/java-agent/${SW_AGENT_VERSION}/apache-skywalking-java-agent-${SW_AGENT_VERSION}.tar.gz \
    && mkdir -p /opt/sw-agent \
    && tar -xzf /tmp/sw-agent.tar.gz -C /opt/sw-agent --strip-components=1 \
    && rm -f /tmp/sw-agent.tar.gz \
    && apt-get purge -y curl tar && apt-get autoremove -y

# ---- 阶段二：运行 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
ARG JAR_FILE=app.jar
ARG SW_AGENT_NAME=mall-service
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxMetaspaceSize=256m" \
    SW_AGENT_NAME=${SW_AGENT_NAME} \
    SW_AGENT_COLLECTOR_BACKEND_SERVICES=127.0.0.1:11800 \
    JAVA_TOOL_OPTIONS="-javaagent:/skywalking/agent/skywalking-agent.jar"
COPY ${JAR_FILE} app.jar
COPY --from=agent /opt/sw-agent /skywalking/agent
RUN useradd -m appuser
USER appuser
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
