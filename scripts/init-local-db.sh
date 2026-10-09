#!/usr/bin/env bash
# ============================================================
#  初始化并启动「仓库自带」的独立 PostgreSQL 实例（端口 5433，trust 认证）
#  适用于 Debian 13 等 Linux；不会影响系统已有的 PostgreSQL 服务。
#
#  与 Windows 版 scripts/init-local-db.cmd 的差异：
#    · 不再硬编码 D:\PostgreSQL18\bin，而是自动在 PATH 或
#      /usr/lib/postgresql/<版本>/bin 中定位 PostgreSQL 工具；
#    · PostgreSQL 禁止以 root 运行，脚本会拒绝 root 身份执行；
#    · 数据目录默认位于 <仓库>/_pg/data，归属当前用户。
#
#  可用环境变量覆盖：PGDATA / PGPORT / DB_NAME / PGLOG
# ============================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PGDATA="${PGDATA:-$REPO_ROOT/_pg/data}"
PGPORT="${PGPORT:-5433}"
DB_NAME="${DB_NAME:-campus_info}"
PGLOG="${PGLOG:-$REPO_ROOT/_pg/pg.log}"
PGHOST="${PGHOST:-127.0.0.1}"

if [ "$(id -u)" = "0" ]; then
  echo "[错误] PostgreSQL 不允许以 root 身份运行 initdb/postgres。" >&2
  echo "       请以普通用户（如 postgres 或你自己的账号）执行本脚本。" >&2
  exit 1
fi

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

PGBIN="${PGBIN:-}"
if [ -z "$PGBIN" ]; then
  PGBIN="$(find_pgbin || true)"
fi
if [ -z "$PGBIN" ]; then
  echo "[错误] 未找到 PostgreSQL 工具链，请先安装：sudo apt install postgresql" >&2
  exit 1
fi
echo "[信息] 使用 PostgreSQL 工具目录：$PGBIN"

mkdir -p "$(dirname "$PGDATA")"

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "[1/3] initdb -> $PGDATA"
  "$PGBIN/initdb" -D "$PGDATA" -U postgres --auth=trust --encoding=UTF8 --locale=C --no-sync
else
  echo "[1/3] 数据目录已存在，跳过 initdb"
fi

echo "[2/3] 启动实例（端口 $PGPORT）..."
"$PGBIN/pg_ctl" -D "$PGDATA" -o "-p $PGPORT -k /tmp" -l "$PGLOG" start

echo "[3/3] 创建数据库 $DB_NAME ..."
if "$PGBIN/psql" -h "$PGHOST" -p "$PGPORT" -U postgres -lqt 2>/dev/null | cut -d '|' -f1 | grep -qw "$DB_NAME"; then
  echo "      数据库 $DB_NAME 已存在，跳过"
else
  "$PGBIN/createdb" -h "$PGHOST" -p "$PGPORT" -U postgres "$DB_NAME"
fi

echo "完成：PostgreSQL 运行在 $PGHOST:$PGPORT，数据库 $DB_NAME"
echo "后端可这样连接："
echo "  DB_URL=jdbc:postgresql://$PGHOST:$PGPORT/$DB_NAME DB_USERNAME=postgres DB_PASSWORD=postgres scripts/start-backend.sh"
