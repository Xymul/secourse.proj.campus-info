#!/usr/bin/env bash
# ============================================================
#  停止本仓库自带的本地 PostgreSQL 实例（默认端口 5433）
#  适用于 Debian 13 等 Linux；等价于 Windows 版 scripts/stop-local-db.cmd
# ============================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PGDATA="${PGDATA:-$REPO_ROOT/_pg/data}"

find_pgbin() {
  if command -v pg_ctl >/dev/null 2>&1; then
    dirname "$(command -v pg_ctl)"
    return 0
  fi
  local candidate
  candidate="$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | sort -V | tail -n 1 || true)"
  if [ -n "$candidate" ]; then
    echo "$candidate"
    return 0
  fi
  return 1
}

PGBIN="${PGBIN:-$(find_pgbin || true)}"
if [ -z "$PGBIN" ]; then
  echo "[错误] 未找到 PostgreSQL 工具链，请先安装：sudo apt install postgresql" >&2
  exit 1
fi

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "[信息] 数据目录 $PGDATA 不存在，无需停止。"
  exit 0
fi

"$PGBIN/pg_ctl" -D "$PGDATA" stop -m fast
echo "已请求停止本地实例；如需删除数据目录，可手动删除 $PGDATA"
