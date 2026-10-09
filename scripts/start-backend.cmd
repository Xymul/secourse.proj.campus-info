@echo off
REM ============================================================
REM  启动后端（Spring Boot + PostgreSQL）
REM  可通过环境变量覆盖数据库连接，例如：
REM    set DB_URL=jdbc:postgresql://127.0.0.1:5433/campus_info
REM ============================================================
setlocal
if "%DB_URL%"=="" set DB_URL=jdbc:postgresql://127.0.0.1:5432/campus_info
if "%DB_USERNAME%"=="" set DB_USERNAME=postgres
if "%DB_PASSWORD%"=="" set DB_PASSWORD=postgres

echo [后端] 数据库：%DB_URL%
echo [后端] 用户：%DB_USERNAME%
cd /d "%~dp0..\backend"
call mvnw.cmd -B spring-boot:run
endlocal
