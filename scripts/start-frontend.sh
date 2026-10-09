#!/usr/bin/env bash
# ============================================================
#  启动前端（Vue 3 + Vite）—— 适用于 Debian 13 等 Linux
#
#  与 Windows 版 scripts/start-frontend.cmd 等价；首次运行自动安装依赖。
#  默认监听 0.0.0.0:5173（服务器上可被外部访问），/api 由 Vite 代理到后端 8080。
#  可通过环境变量调整：
#    CAMPUS_DEV_HOST=127.0.0.1  仅本机访问（默认 0.0.0.0）
#    CAMPUS_DEV_PORT=5173       监听端口
#    CAMPUS_API_PROXY=http://127.0.0.1:8080  后端地址
# ============================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT/frontend"

export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"

# 服务器部署默认监听所有网卡，便于通过 http://<服务器IP>:5173 访问
export CAMPUS_DEV_HOST="${CAMPUS_DEV_HOST:-0.0.0.0}"

if ! command -v node >/dev/null 2>&1; then
  echo "[错误] 未找到 node，请先安装 Node.js 18+（Debian: sudo apt install nodejs npm）" >&2
  exit 1
fi

if [ ! -d node_modules ]; then
  echo "[前端] 首次运行，安装依赖..."
  npm install --no-audit --no-fund
fi

echo "[前端] 启动开发服务器（$CAMPUS_DEV_HOST:${CAMPUS_DEV_PORT:-5173}）..."
exec npm run dev
