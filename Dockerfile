# ---------- frontend build stage ----------
FROM node:24-alpine AS frontend
WORKDIR /app/frontend

# 同樣的快取邏輯：先只 COPY 依賴清單，改前端原始碼不會讓 npm ci 那層失效
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-fund --no-audit

COPY frontend/ ./
# vite.config.js 的 outDir 是 ../src/main/resources/static，
# 在這個 WORKDIR 底下會解析成 /app/src/main/resources/static
RUN npm run build

# ---------- backend build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# pom.xml 與 src 分兩步 COPY，是為了 Docker 的分層快取。
# 一次 COPY 整個專案的話，改一行 Java 就會讓「下載依賴」那層失效，
# 每次 build 都要重抓幾百 MB。分開之後只有 pom.xml 變動才會重抓。
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
# 把前端的產出放進 static/，跟後端打包成同一個 jar——同源部署，完全不需要 CORS
COPY --from=frontend /app/src/main/resources/static ./src/main/resources/static
# 這裡跳過測試，因為測試由 CI 負責。
# 而且測試用 Testcontainers 需要 Docker daemon，build 階段的容器裡沒有。
RUN mvn clean package -DskipTests -B

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 容器裡預設是 root。應用一旦被入侵，攻擊者在容器內就是 root。
RUN addgroup -S app && adduser -S app -G app

# multi-stage 的重點：build 階段需要完整 Maven + JDK（500MB+），執行只需要 JRE。
# 分階段後最終 image 不含原始碼與 build 工具——體積和安全兩個考量同時成立。
COPY --from=build /app/target/*.jar app.jar

USER app
EXPOSE 8080

# 容器有自己的記憶體上限，讓 JVM 依照 cgroup 限制而不是實體機器的記憶體決定 heap
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
