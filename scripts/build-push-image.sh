#!/usr/bin/env bash
# 构建 Maven 项目，生成 Docker 镜像 (linux/amd64)，并推送到私有仓库。
#
# 用法:
#   ./scripts/build-push-image.sh                              # 使用默认镜像标签
#   ./scripts/build-push-image.sh docker.jasolar.com/base/nexus/ja-repo:lastest
#   KKREPO_IMAGE_TAG=xxx ./scripts/build-push-image.sh        # 通过环境变量指定标签
#
# 可选环境变量:
#   KKREPO_IMAGE_TAG  - 镜像标签 (默认 docker.jasolar.com/base/nexus/ja-repo:lastest)
#   KKREPO_SKIP_MAVEN - 设为 1 可跳过 Maven 构建步骤
set -euo pipefail

# ─── 目录与变量 ───────────────────────────────────────────────
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

IMAGE_TAG="${1:-${KKREPO_IMAGE_TAG:-docker.jasolar.com/base/nexus/ja-repo:lastest}}"
SKIP_MAVEN="${KKREPO_SKIP_MAVEN:-0}"
PLATFORM="linux/amd64"

# 自动从 pom.xml 读取项目版本
PROJECT_VERSION="$(mvn -q -N -DforceStdout help:evaluate -Dexpression=project.version)"
JAR_FILE="server/target/kkrepo-server-${PROJECT_VERSION}.jar"
START_CLASS="com.github.klboke.kkrepo.server.KkRepoApplication"

echo "========================================="
echo " 构建 & 推送 Docker 镜像"
echo " 镜像标签 : ${IMAGE_TAG}"
echo " 平台     : ${PLATFORM}"
echo " 项目版本 : ${PROJECT_VERSION}"
echo "========================================="

# ─── 1. Maven 构建 ───────────────────────────────────────────
if [[ "$SKIP_MAVEN" == "1" ]]; then
  echo "[1/4] 跳过 Maven 构建 (KKREPO_SKIP_MAVEN=1)"
else
  echo "[1/4] 执行 Maven 构建 (mvn -pl server -am -DskipTests package spring-boot:repackage)..."
  mvn -pl server -am -DskipTests package spring-boot:repackage
fi

# ─── 2. 验证可执行 JAR ───────────────────────────────────────
echo "[2/4] 验证 Spring Boot 可执行 JAR..."

if [[ ! -f "$JAR_FILE" ]]; then
  echo "[ERROR] JAR 文件不存在: $JAR_FILE" >&2
  echo "       请确认 Maven 构建是否成功，或使用 KKREPO_SKIP_MAVEN=1 跳过构建。" >&2
  exit 1
fi

manifest="$(unzip -p "$JAR_FILE" META-INF/MANIFEST.MF)"

if [[ "$manifest" != *"Main-Class: org.springframework.boot.loader.launch.JarLauncher"* ]]; then
  echo "[ERROR] $JAR_FILE 不是 Spring Boot 可执行 JAR: 缺少 JarLauncher manifest" >&2
  exit 1
fi

if [[ "$manifest" != *"Start-Class: $START_CLASS"* ]]; then
  echo "[ERROR] $JAR_FILE 不是 Spring Boot 可执行 JAR: 缺少 Start-Class $START_CLASS" >&2
  exit 1
fi

echo "       JAR 验证通过: $JAR_FILE ($(du -h "$JAR_FILE" | cut -f1))"

# ─── 3. 构建 Docker 镜像 ─────────────────────────────────────
echo "[3/4] 构建 Docker 镜像 (${PLATFORM})..."
docker build \
  --platform "$PLATFORM" \
  --build-arg "JAR_FILE=$JAR_FILE" \
  -t "$IMAGE_TAG" \
  .

echo "       镜像构建成功: $IMAGE_TAG"

# ─── 4. 推送到私有仓库 ──────────────────────────────────────
echo "[4/4] 推送镜像到 ${IMAGE_TAG} ..."
docker push "$IMAGE_TAG"

echo "========================================="
echo " 完成! 镜像已推送到: ${IMAGE_TAG}"
echo "========================================="
