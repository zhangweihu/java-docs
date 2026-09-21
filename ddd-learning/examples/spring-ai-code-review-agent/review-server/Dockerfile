# 多阶段构建：第一阶段编译，第二阶段运行
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY ../pom.xml ./
COPY ../review-core ./review-core
COPY ../review-subagents ./review-subagents
COPY . ./review-server
RUN mvn -pl review-server -am clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/review-server/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]