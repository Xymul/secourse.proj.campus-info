@echo off
REM 初始化一个独立的本地 PostgreSQL 实例（端口 5433，trust 认证），用于开发验证
REM 不会影响机器上已有的 PostgreSQL 服务实例
set PGBIN=D:\PostgreSQL18\bin
set PGDATA=D:\AAtmp\_pg\data
set PGPORT=5433

if not exist "%PGDATA%" (
  echo [1/3] initdb ...
  "%PGBIN%\initdb.exe" -D "%PGDATA%" -U postgres --auth=trust --encoding=UTF8 --locale=C --no-sync
) else (
  echo [1/3] 数据目录已存在，跳过 initdb
)

echo [2/3] 启动实例（端口 %PGPORT%）...
"%PGBIN%\pg_ctl.exe" -D "%PGDATA%" -o "-p %PGPORT%" -l "D:\AAtmp\_pg\pg.log" start

echo [3/3] 创建数据库 campus_info ...
"%PGBIN%\createdb.exe" -h 127.0.0.1 -p %PGPORT% -U postgres campus_info
echo done
