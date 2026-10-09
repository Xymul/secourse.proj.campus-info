#!/usr/bin/env bash
# ============================================================
#  停止后端服务（Spring Boot）
#
#  用法：
#    scripts/stop-backend.sh
#    BACKEND_PORT=8081 scripts/stop-backend.sh
#    GRACE=15 scripts/stop-backend.sh      # 优雅退出等待秒数（默认 8）
#
#  注意：若后端由 systemd（campus-backend.service）托管，请改用
#        sudo systemctl stop campus-backend，否则会被自动拉起。
# ============================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PIDFILE="$REPO_ROOT/_build/run/backend.pid"
BACKEND_PORT="${BACKEND_PORT:-${SERVER_PORT:-8080}}"
GRACE="${GRACE:-8}"

SUDO=""
{ [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1; } && SUDO="sudo"

port_pids() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    $SUDO ss -ltnpH 2>/dev/null | awk -v p=":$port" '$4 ~ p"$" {print}' \
      | grep -oE 'pid=[0-9]+' | cut -d= -f2
  elif command -v lsof >/dev/null 2>&1; then
    $SUDO lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null
  elif command -v fuser >/dev/null 2>&1; then
    $SUDO fuser "$port"/tcp 2>/dev/null | tr -s ' ' '\n'
  fi
}
pattern_pids() { pgrep -f "$1" 2>/dev/null || true; }
pidfile_pid() {
  local f="$1"; [ -f "$f" ] || return 0
  local p; p="$(tr -dc '0-9' < "$f" 2>/dev/null)"
  [ -n "$p" ] && kill -0 "$p" 2>/dev/null && echo "$p"
  rm -f "$f"
}
dedupe() { tr ' ' '\n' | grep -E '^[0-9]+$' | sort -u | grep -vx "$$" | tr '\n' ' '; }

PIDS="$(pidfile_pid "$PIDFILE") \
$(port_pids "$BACKEND_PORT") \
$(pattern_pids 'campus-info-platform-backend-.*\.jar') \
$(pattern_pids 'spring-boot:run') \
$(pattern_pids 'org\.secourse\.hzau\.campus\.CampusInfoApplication')"
PIDS="$(printf '%s' "$PIDS" | dedupe)"

if [ -z "${PIDS// /}" ]; then
  echo "[后端] 未发现运行中的进程（端口 $BACKEND_PORT 空闲）"
  exit 0
fi

echo "[后端] 停止 PID：$PIDS"
# shellcheck disable=SC2086
kill $PIDS 2>/dev/null || true

i=0; alive=""
while [ "$i" -lt "$GRACE" ]; do
  alive=""
  for p in $PIDS; do kill -0 "$p" 2>/dev/null && alive="$alive $p"; done
  [ -z "${alive// /}" ] && { echo "[后端] 已优雅退出"; break; }
  sleep 1; i=$((i + 1))
done

if [ -n "${alive// /}" ]; then
  echo "[后端] 宽限期结束，强制结束：$alive"
  # shellcheck disable=SC2086
  kill -9 $alive 2>/dev/null || true
  sleep 1
fi

if $SUDO ss -ltn 2>/dev/null | awk -v p=":$BACKEND_PORT" '$4 ~ p"$" {f=1} END{exit !f}'; then
  echo "⚠️  端口 $BACKEND_PORT 仍被占用：$SUDO ss -ltnp | grep ':$BACKEND_PORT'"
else
  echo "✅ 端口 $BACKEND_PORT 已释放"
fi
