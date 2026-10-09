#!/usr/bin/env bash
# ============================================================
#  一键构建（Debian 13 等 Linux）：后端可执行 jar + 前端静态产物
#
#  产物：
#    backend/target/campus-info-platform-backend-1.0.0.jar
#    frontend/dist/
#
#  用法：
#    scripts/build.sh              # 构建全部
#    scripts/build.sh backend      # 仅后端
#    scripts/build.sh frontend     # 仅前端
# ============================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-all}"

export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"

build_backend() {
  echo "==== 构建后端 ===="
  cd "$REPO_ROOT/backend"
  # 优先系统 mvn，其次仓库自带 mvnw
  if command -v mvn >/dev/null 2>&1; then
    mvn -B -DskipTests package
  else
    [ -x ./mvnw ] || chmod +x ./mvnw 2>/dev/null || true
    if [ -x ./mvnw ]; then
      ./mvnw -B -DskipTests package
    else
      sh ./mvnw -B -DskipTests package
    fi
  fi
  echo "后端产物：backend/target/campus-info-platform-backend-1.0.0.jar"
}

build_frontend() {
  echo "==== 构建前端 ===="
  cd "$REPO_ROOT/frontend"
  if [ -f package-lock.json ]; then
    npm ci --no-audit --no-fund
  else
    npm install --no-audit --no-fund
  fi
  npm run build
  echo "前端产物：frontend/dist/"
}

case "$TARGET" in
  all)      build_backend; build_frontend ;;
  backend)  build_backend ;;
  frontend) build_frontend ;;
  *) echo "用法：scripts/build.sh [all|backend|frontend]" >&2; exit 2 ;;
esac

echo "==== 构建完成 ===="
