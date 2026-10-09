#!/usr/bin/env bash
# ============================================================
#  一键启动：后端 + 前端 + Nginx
#
#  用法：
#    scripts/start-all.sh                     # auto：有 frontend/dist 就走 Nginx(prod)，否则 Vite(dev)
#    FRONTEND_MODE=prod scripts/start-all.sh  # 生产：Nginx 托管 frontend/dist
#    FRONTEND_MODE=dev  scripts/start-all.sh  # 开发：Vite dev server(5173)
#    BUILD=1 scripts/start-all.sh             # 先构建后端 jar + 前端 dist
#    NO_NGINX=1 scripts/start-all.sh          # 不起 Nginx
#
#  日志 / PID：_build/run/{backend.log,frontend.log,backend.pid,frontend.pid}
# ============================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$REPO_ROOT/_build/run"
mkdir -p "$RUN_DIR"

BACKEND_PORT="${BACKEND_PORT:-${SERVER_PORT:-8080}}"
FRONTEND_PORT="${FRONTEND_PORT:-${CAMPUS_DEV_PORT:-5173}}"
FRONTEND_MODE="${FRONTEND_MODE:-auto}"
NO_NGINX="${NO_NGINX:-0}"
BUILD="${BUILD:-0}"
WAIT_SECONDS="${WAIT_SECONDS:-90}"
JAR="$REPO_ROOT/backend/target/campus-info-platform-backend-1.0.0.jar"

# 后端数据库连接（按需覆盖）
export DB_URL="${DB_URL:-jdbc:postgresql://127.0.0.1:5432/campus_info}"
export DB_USERNAME="${DB_USERNAME:-postgres}"
export DB_PASSWORD="${DB_PASSWORD:-postgres}"

SUDO=""
{ [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1; } && SUDO="sudo"

# ---------- 工具 ----------
port_in_use() {
  ss -ltn 2>/dev/null | awk -v p=":$1" '$4 ~ p"$" {f=1} END{exit !f}'
}
wait_port() {
  local port="$1" name="$2" log="$3" i=0
  while [ "$i" -lt "$WAIT_SECONDS" ]; do
    if port_in_use "$port"; then echo "[$name] 就绪（端口 $port）"; return 0; fi
    sleep 1; i=$((i + 1))
  done
  echo "⚠️  [$name] ${WAIT_SECONDS}s 内未监听 $port，请查看 $RUN_DIR/$log"
  return 1
}

# ---------- 0) 可选构建 ----------
if [ "$BUILD" = "1" ]; then
  echo "==== 构建产物 ===="
  "$REPO_ROOT/scripts/build.sh" || exit 1
fi

# ---------- 1) 后端 ----------
start_backend() {
  if port_in_use "$BACKEND_PORT"; then
    echo "[后端] 端口 $BACKEND_PORT 已在监听，跳过"
    return 0
  fi
  cd "$REPO_ROOT/backend" || return 1
  if [ -f "$JAR" ]; then
    echo "[后端] 启动 jar：$(basename "$JAR")"
    nohup java -Dfile.encoding=UTF-8 -jar "$JAR" > "$RUN_DIR/backend.log" 2>&1 &
  else
    echo "[后端] 无 jar，回退 spring-boot:run（首次会下载依赖）"
    local mvn="mvn"
    if ! command -v mvn >/dev/null 2>&1; then
      [ -x ./mvnw ] || chmod +x ./mvnw 2>/dev/null || true
      mvn="./mvnw"
    fi
    nohup $mvn -B spring-boot:run > "$RUN_DIR/backend.log" 2>&1 &
  fi
  echo $! > "$RUN_DIR/backend.pid"
  echo "[后端] PID $(cat "$RUN_DIR/backend.pid")，日志 $RUN_DIR/backend.log"
}

# ---------- 2) 前端 ----------
resolve_frontend_mode() {
  if [ "$FRONTEND_MODE" = "auto" ]; then
    if [ -f "$REPO_ROOT/frontend/dist/index.html" ]; then
      FRONTEND_MODE="prod"
    else
      FRONTEND_MODE="dev"
    fi
  fi
  echo "[前端] 模式：$FRONTEND_MODE"
}

start_frontend() {
  if [ "$FRONTEND_MODE" = "prod" ]; then
    if [ ! -f "$REPO_ROOT/frontend/dist/index.html" ]; then
      echo "[前端] prod 模式缺少 frontend/dist，请先：scripts/build.sh frontend" >&2
      return 1
    fi
    echo "[前端] prod：由 Nginx 托管 frontend/dist（无独立进程）"
    return 0
  fi
  if port_in_use "$FRONTEND_PORT"; then
    echo "[前端] 端口 $FRONTEND_PORT 已在监听，跳过"
    return 0
  fi
  cd "$REPO_ROOT/frontend" || return 1
  [ -d node_modules ] || npm install --no-audit --no-fund
  export CAMPUS_DEV_HOST="${CAMPUS_DEV_HOST:-0.0.0.0}"
  export CAMPUS_API_PROXY="${CAMPUS_API_PROXY:-http://127.0.0.1:$BACKEND_PORT}"
  echo "[前端] 启动 Vite dev（$CAMPUS_DEV_HOST:$FRONTEND_PORT）"
  nohup npm run dev > "$RUN_DIR/frontend.log" 2>&1 &
  echo $! > "$RUN_DIR/frontend.pid"
  echo "[前端] PID $(cat "$RUN_DIR/frontend.pid")，日志 $RUN_DIR/frontend.log"
}

# ---------- 3) Nginx ----------
start_nginx() {
  [ "$NO_NGINX" = "1" ] && { echo "[Nginx] NO_NGINX=1，跳过"; return 0; }
  if ! command -v nginx >/dev/null 2>&1; then
    echo "[Nginx] 未安装，跳过（sudo apt install nginx）"; return 0
  fi
  if [ "$FRONTEND_MODE" != "prod" ]; then
    echo "[Nginx] dev 模式下不起 Nginx（/api 由 Vite 代理）"; return 0
  fi
  $SUDO nginx -t || return 1
  if systemctl is-active --quiet nginx 2>/dev/null; then
    $SUDO systemctl reload nginx && echo "[Nginx] 已 reload（配置/静态文件生效）"
  else
    $SUDO systemctl start nginx && echo "[Nginx] 已启动"
  fi
}

# ---------- 执行 ----------
resolve_frontend_mode
start_backend
wait_port "$BACKEND_PORT" "后端" "backend.log"
start_frontend
if [ "$FRONTEND_MODE" = "dev" ]; then
  wait_port "$FRONTEND_PORT" "前端" "frontend.log"
fi
start_nginx

IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
echo
echo "==== 启动完成 ===="
echo "后端接口：http://127.0.0.1:$BACKEND_PORT/api/  (探针: /api/schools)"
if [ "$FRONTEND_MODE" = "prod" ]; then
  echo "前端页面：http://${IP:-<服务器IP>}/            （Nginx 80 -> frontend/dist）"
else
  echo "前端页面：http://${IP:-<服务器IP>}:$FRONTEND_PORT/   （Vite dev）"
fi
echo "日志目录：$RUN_DIR"
echo "停止服务：scripts/stop-backend.sh  scripts/stop-frontend.sh  scripts/stop-nginx.sh"
