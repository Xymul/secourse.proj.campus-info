#!/usr/bin/env bash
# ============================================================
#  启动后端（Spring Boot + PostgreSQL）—— 适用于 Debian 13 等 Linux
#
#  与 Windows 版 scripts/start-backend.cmd 等价，可用环境变量覆盖数据库连接：
#    DB_URL=jdbc:postgresql://127.0.0.1:5433/campus_info scripts/start-backend.sh
#    DB_USERNAME / DB_PASSWORD / CAMPUS_SEED_ENABLED / SPRING_PROFILES_ACTIVE 同样生效
# ============================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT/backend"

# 保证中文源码 / 日志按 UTF-8 处理（Debian 服务器若未生成 locale，请用 C.UTF-8，内置无需额外生成）
export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"

export DB_URL="${DB_URL:-jdbc:postgresql://127.0.0.1:5432/campus_info}"
export DB_USERNAME="${DB_USERNAME:-postgres}"
export DB_PASSWORD="${DB_PASSWORD:-postgres}"

echo "[后端] 数据库：$DB_URL"
echo "[后端] 用户：$DB_USERNAME"

# Maven：优先使用系统 mvn，否则退回仓库自带的 Maven Wrapper（首次运行会自动下载 Maven 3.9.16）
if command -v mvn >/dev/null 2>&1; then
  exec mvn -B spring-boot:run
fi

# Maven Wrapper 需要可执行权限（从 Windows 拷贝过来时常常丢失）
[ -x ./mvnw ] || chmod +x ./mvnw 2>/dev/null || true
if [ -x ./mvnw ]; then
  exec ./mvnw -B spring-boot:run
else
  exec sh ./mvnw -B spring-boot:run
fi
