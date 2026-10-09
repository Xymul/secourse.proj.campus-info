#!/usr/bin/env bash
# ============================================================
#  停止 Nginx 服务
#
#  用法：
#    scripts/stop-nginx.sh
#    CHECK_PORT=0 scripts/stop-nginx.sh    # 跳过端口兜底清理
#    NGINX_HTTP_PORT=8080 scripts/stop-nginx.sh
# ============================================================
set -uo pipefail

NGINX_HTTP_PORT="${NGINX_HTTP_PORT:-80}"
CHECK_PORT="${CHECK_PORT:-1}"

SUDO=""
{ [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1; } && SUDO="sudo"

if ! command -v nginx >/dev/null 2>&1; then
  echo "[Nginx] 未安装，无需停止（sudo apt install nginx）"
  exit 0
fi

if command -v systemctl >/dev/null 2>&1; then
  if systemctl is-active --quiet nginx 2>/dev/null; then
    echo "[Nginx] 通过 systemd 停止"
    $SUDO systemctl stop nginx && echo "[Nginx] 已停止"
  else
    echo "[Nginx] systemd 中未在运行"
  fi
else
  echo "[Nginx] 无 systemd，尝试 nginx -s stop"
  $SUDO nginx -s stop 2>/dev/null && echo "[Nginx] 已停止" || echo "[Nginx] 未在运行"
fi

# 兜底：若端口仍被占用，按端口清理残留进程
if [ "$CHECK_PORT" = "1" ]; then
  if $SUDO ss -ltn 2>/dev/null | awk -v p=":$NGINX_HTTP_PORT" '$4 ~ p"$" {f=1} END{exit !f}'; then
    PIDS="$($SUDO ss -ltnpH 2>/dev/null | awk -v p=":$NGINX_HTTP_PORT" '$4 ~ p"$" {print}' \
      | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u | tr '\n' ' ')"
    if [ -n "${PIDS// /}" ]; then
      echo "[Nginx] 端口 $NGINX_HTTP_PORT 仍被占用，清理 PID：$PIDS"
      # shellcheck disable=SC2086
      $SUDO kill $PIDS 2>/dev/null || true
      sleep 1
      # shellcheck disable=SC2086
      $SUDO kill -9 $PIDS 2>/dev/null || true
    fi
  fi
  if $SUDO ss -ltn 2>/dev/null | awk -v p=":$NGINX_HTTP_PORT" '$4 ~ p"$" {f=1} END{exit !f}'; then
    echo "⚠️  端口 $NGINX_HTTP_PORT 仍被占用：$SUDO ss -ltnp | grep ':$NGINX_HTTP_PORT'"
  else
    echo "✅ 端口 $NGINX_HTTP_PORT 已释放"
  fi
fi

echo "完成。"
