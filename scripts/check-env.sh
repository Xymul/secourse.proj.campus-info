#!/usr/bin/env bash
# ============================================================
#  环境自检（Debian 13 等 Linux）：检查运行本项目所需组件是否就绪
#  用法：scripts/check-env.sh
# ============================================================
set -uo pipefail

ok=0
fail=0

check() {
  local name="$1" cmd="$2" hint="$3"
  if command -v "$cmd" >/dev/null 2>&1; then
    printf '[OK]   %-12s %s\n' "$name" "$("$cmd" --version 2>&1 | head -n 1)"
    ok=$((ok + 1))
  else
    printf '[缺失] %-12s 未安装 —— %s\n' "$name" "$hint"
    fail=$((fail + 1))
  fi
}

echo "==== 此刻校园 · 运行环境自检 ===="
check "JDK"    java   "sudo apt install openjdk-21-jdk"
check "Node.js" node  "sudo apt install nodejs npm（建议 Node 18+）"
check "npm"    npm    "sudo apt install nodejs npm"

# Maven：仓库自带 mvnw 包装器，无需系统安装
if [ -x "$(dirname "${BASH_SOURCE[0]}")/../backend/mvnw" ]; then
  echo "[OK]   Maven      使用仓库自带 backend/mvnw（无需系统安装）"
  ok=$((ok + 1))
else
  echo "[提示] Maven      将使用 backend/mvnw（首次运行会自动下载，需可执行权限）"
fi

# PostgreSQL：客户端工具在 PATH 或 /usr/lib/postgresql/*/bin
if command -v psql >/dev/null 2>&1; then
  echo "[OK]   PostgreSQL $(psql --version 2>&1 | head -n 1)"
  ok=$((ok + 1))
elif ls -d /usr/lib/postgresql/*/bin >/dev/null 2>&1; then
  echo "[OK]   PostgreSQL 位于 /usr/lib/postgresql/*/bin"
  ok=$((ok + 1))
else
  echo "[缺失] PostgreSQL 未安装 —— sudo apt install postgresql（或使用 docker compose up -d postgres）"
  fail=$((fail + 1))
fi

# curl（冒烟测试用）
if command -v curl >/dev/null 2>&1; then
  echo "[OK]   curl       $(curl --version 2>&1 | head -n 1)"
  ok=$((ok + 1))
else
  echo "[缺失] curl       未安装 —— sudo apt install curl"
  fail=$((fail + 1))
fi

# python3（脚本化冒烟测试用）
if command -v python3 >/dev/null 2>&1; then
  echo "[OK]   python3    $(python3 --version 2>&1)"
  ok=$((ok + 1))
else
  echo "[提示] python3    未安装 —— sudo apt install python3（运行 scripts/smoke-test.py 需要）"
fi

echo "==== 就绪 $ok 项，缺失 $fail 项 ===="
[ "$fail" -eq 0 ]
