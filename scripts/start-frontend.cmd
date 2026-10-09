@echo off
REM ============================================================
REM  启动前端（Vue 3 + Vite，默认 http://127.0.0.1:5173）
REM  首次运行会自动安装依赖；开发环境下 /api 代理到后端 8080
REM ============================================================
setlocal
cd /d "%~dp0..\frontend"

if not exist node_modules (
  echo [前端] 首次运行，安装依赖...
  call npm install --no-audit --no-fund
)

echo [前端] 启动开发服务器...
call npm run dev
endlocal
