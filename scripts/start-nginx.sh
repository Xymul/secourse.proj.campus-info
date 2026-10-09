#!/usr/bin/env bash
# ============================================================
#  启动 Nginx（可选：启用本项目的 campus 站点）
#
#  用法：
#    scripts/start-nginx.sh                      # 校验配置后启动（已在运行则 reload）
#    ENABLE_SITE=1 scripts/start-nginx.sh        # 先把 deploy/nginx-campus.conf 装成站点再启动
#    RELOAD_ONLY=1 scripts/start-nginx.sh        # 只 reload，不改变运行状态
#    NGINX_HTTP_PORT=8080 scripts/start-nginx.sh # 监听端口不是 80 时（用于自检）
# ============================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NGINX_HTTP_PORT="${NGINX_HTTP_PORT:-80}"
ENABLE_SITE="${ENABLE_SITE:-0}"
RELOAD_ONLY="${RELOAD_ONLY:-0}"
WAIT_SECONDS="${WAIT_SECONDS:-15}"
SITE_NAME="campus"

SUDO=""
{ [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1; } && SUDO="sudo"

has_systemd() { command -v systemctl >/dev/null 2>&1; }
port_in_use() { $SUDO ss -ltn 2>/dev/null | awk -v p=":$1" '$4 ~ p"$" {f=1} END{exit !f}'; }

# ---------- 0) 前置检查 ----------
if ! command -v nginx >/dev/null 2>&1; then
  echo "[Nginx] 未安装，请先执行：sudo apt install nginx" >&2
  exit 1
fi

# ---------- 1) 可选：启用本项目站点 ----------
if [ "$ENABLE_SITE" = "1" ]; then
  SRC="$REPO_ROOT/deploy/nginx-campus.conf"
  if [ ! -f "$SRC" ]; then
    echo "[Nginx] 未找到站点模板：$SRC" >&2
    exit 1
  fi
  $SUDO cp "$SRC" "/etc/nginx/sites-available/$SITE_NAME"
  $SUDO ln -sf "/etc/nginx/sites-available/$SITE_NAME" "/etc/nginx/sites-enabled/$SITE_NAME"
  if [ -e /etc/nginx/sites-enabled/default ]; then
    $SUDO rm -f /etc/nginx/sites-enabled/default
    echo "[Nginx] 已移除默认站点 default"
  fi
  echo "[Nginx] 已启用站点：/etc/nginx/sites-enabled/$SITE_NAME"
fi

# ---------- 2) 配置校验 ----------
echo "[Nginx] 校验配置..."
if ! $SUDO nginx -t; then
  echo "[Nginx] 配置校验失败，已中止启动" >&2
  exit 1
fi

# ---------- 3) 启动 / reload ----------
if [ "$RELOAD_ONLY" = "1" ]; then
  if has_systemd; then
    $SUDO systemctl reload nginx && echo "[Nginx] 已 reload"
  else
    $SUDO nginx -s reload && echo "[Nginx] 已 reload"
  fi
elif has_systemd && systemctl is-active --quiet nginx 2>/dev/null; then
  $SUDO systemctl reload nginx && echo "[Nginx] 已在运行，已 reload（配置/静态文件生效）"
elif has_systemd; then
  $SUDO systemctl start nginx && echo "[Nginx] 已启动（systemd）"
else
  $SUDO nginx && echo "[Nginx] 已启动（非 systemd）"
fi

# ---------- 4) 等待端口就绪 ----------
i=0
while [ "$i" -lt "$WAIT_SECONDS" ]; do
  port_in_use "$NGINX_HTTP_PORT" && break
  sleep 1; i=$((i + 1))
done

if ! port_in_use "$NGINX_HTTP_PORT"; then
  echo "[Nginx] ⚠️  端口 $NGINX_HTTP_PORT 未监听，请查看日志：" >&2
  echo "        $SUDO journalctl -u nginx -n 50 --no-pager" >&2
  exit 1
fi
echo "[Nginx] 已监听端口 $NGINX_HTTP_PORT"

# ---------- 5) 自检 ----------
if command -v curl >/dev/null 2>&1; then
  STATUS="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$NGINX_HTTP_PORT/" || true)"
  echo "[Nginx] 自检 GET / -> HTTP $STATUS"
  if [ "$STATUS" != "200" ]; then
    echo "        提示：确认站点 root 指向已构建的 frontend/dist（deploy/nginx-campus.conf 默认 /opt/campus/frontend/dist）"
  fi
fi

IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
echo
echo "==== Nginx 就绪 ===="
echo "访问地址：http://${IP:-<服务器IP>}${NGINX_HTTP_PORT:+:$NGINX_HTTP_PORT}/"
echo "提示：确保站点 proxy_pass 的端口与后端一致（默认 8080）。"
echo "停止：scripts/stop-nginx.sh"
