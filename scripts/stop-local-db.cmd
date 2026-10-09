@echo off
REM 停止本仓库自带的本地 PostgreSQL 实例（端口 5433）
set PGBIN=D:\PostgreSQL18\bin
set PGDATA=D:\AAtmp\_pg\data
"%PGBIN%\pg_ctl.exe" -D "%PGDATA%" stop -m fast
echo 已请求停止本地实例；如需删除数据目录，可手动删除 %PGDATA%
