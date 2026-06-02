# ============================================================
# FitCoach Server — 多阶段 Docker 构建
#
# Stage 1 (builder): 用 Maven 镜像把多模块项目打成 fat jar
#   - 优先复制 pom.xml 走依赖缓存层，源码变更不重新下载依赖
#   - -pl fitcoach-app -am 只构建启动模块及其依赖
#   - -DskipTests：CI 已跑过测试；上线构建不重复跑（如需可去掉 --skip-tests 重建）
#
# Stage 2 (runtime): JRE-only 极简镜像，体积约 250MB
#   - 用 eclipse-temurin:17-jre 而非 openjdk —— 官方维护更稳定
#   - 创建非 root 用户 fitcoach 运行进程（容器安全最佳实践）
#   - 暴露 8080，HEALTHCHECK 走 /api/auth/ping
#   - JAVA_OPTS 可通过 docker-compose 环境变量覆盖
#
# 构建：
#   docker build -t fitcoach-server:latest .
#
# 运行（独立）：
#   docker run -d --name fitcoach-app -p 8080:8080 \
#       -e SPRING_PROFILES_ACTIVE=prod \
#       -e DB_URL=jdbc:mysql://host.docker.internal:3306/fitcoach \
#       -e DB_PASSWORD=xxx \
#       -e CORS_ALLOWED_ORIGINS=https://admin.example.com \
#       -v /data/fitcoach/uploads:/app/uploads \
#       fitcoach-server:latest
#
# 一般通过 docker-compose.prod.yml 编排，不直接 docker run。
# ============================================================

# ====== Stage 1: 构建 ======
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# --- 依赖层（变化少，放在前面利用 Docker layer cache） ---
# 先拷所有 pom.xml（父 + 各子模块），跑一次 dependency:go-offline 把依赖拉到 ~/.m2
# 这样后续只改 src 不改 pom 时，不会重新下依赖
COPY pom.xml ./
COPY fitcoach-common/pom.xml         fitcoach-common/
COPY fitcoach-login/pom.xml          fitcoach-login/
COPY fitcoach-feedback/pom.xml       fitcoach-feedback/
COPY fitcoach-log/pom.xml            fitcoach-log/
COPY fitcoach-clientbus/pom.xml      fitcoach-clientbus/
COPY fitcoach-exercise/pom.xml       fitcoach-exercise/
COPY fitcoach-training-record/pom.xml fitcoach-training-record/
COPY fitcoach-membership/pom.xml     fitcoach-membership/
COPY fitcoach-payment/pom.xml        fitcoach-payment/
COPY fitcoach-appversion/pom.xml     fitcoach-appversion/
COPY fitcoach-admin/pom.xml          fitcoach-admin/
COPY fitcoach-app/pom.xml            fitcoach-app/

# 预热依赖（失败不致命 —— 多模块互相引用时 go-offline 偶尔会有边界情况）
RUN mvn -B -pl fitcoach-app -am dependency:go-offline -DskipTests || true

# --- 源码层 ---
COPY fitcoach-common         fitcoach-common
COPY fitcoach-login          fitcoach-login
COPY fitcoach-feedback       fitcoach-feedback
COPY fitcoach-log            fitcoach-log
COPY fitcoach-clientbus      fitcoach-clientbus
COPY fitcoach-exercise       fitcoach-exercise
COPY fitcoach-training-record fitcoach-training-record
COPY fitcoach-membership     fitcoach-membership
COPY fitcoach-payment        fitcoach-payment
COPY fitcoach-appversion     fitcoach-appversion
COPY fitcoach-admin          fitcoach-admin
COPY fitcoach-app            fitcoach-app

# 构建 fat jar（fitcoach-app 模块，连带依赖一起 -am）
RUN mvn -B -pl fitcoach-app -am clean package -DskipTests \
    && cp fitcoach-app/target/fitcoach-app-*.jar /build/app.jar

# ====== Stage 2: 运行时 ======
FROM eclipse-temurin:17-jre

# 安装 curl 用于 HEALTHCHECK；tzdata 保证容器内时区正确（Asia/Shanghai）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 非 root 用户跑应用（限制容器逃逸时的危害面）
RUN groupadd -r fitcoach && useradd -r -g fitcoach -d /app -s /bin/bash fitcoach

WORKDIR /app

# 上传目录（默认在镜像内，生产建议通过 -v 挂宿主机持久化）
RUN mkdir -p /app/uploads /app/logs && chown -R fitcoach:fitcoach /app

COPY --from=builder --chown=fitcoach:fitcoach /build/app.jar /app/app.jar

USER fitcoach

# JVM 调优默认值；可被 docker-compose / docker run 的 -e JAVA_OPTS 覆盖
ENV JAVA_OPTS="-Xms256m -Xmx1g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    TZ=Asia/Shanghai

EXPOSE 8080

# 健康检查：每 30s 调 /api/auth/ping，连续 3 次失败标 unhealthy
# 注意：start-period 60s 给应用启动留 buffer，避免误判
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:${SERVER_PORT}/api/auth/ping || exit 1

# 用 exec 形式 + sh -c 是为了让 $JAVA_OPTS 能被 shell 展开
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
