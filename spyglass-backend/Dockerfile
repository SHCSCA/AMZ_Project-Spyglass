# Multi-stage build for Spyglass backend
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
# 预下载依赖，加快后续构建（使用构建缓存）
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

# Stage 2: Runtime (Slim JRE)
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai" \
    SPRING_PROFILES_ACTIVE=prod
WORKDIR /app
# Copy built artifact
COPY --from=build /workspace/target/*.jar app.jar
# Create non-root user
RUN useradd -u 1001 -r -s /sbin/nologin appuser && chown -R appuser /app
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=40s CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
