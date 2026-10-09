# 部署资产说明（Debian 13）

本目录提供把「此刻校园」作为常驻服务部署到 Debian 13 服务器时所需的模板文件。

| 文件 | 用途 |
| --- | --- |
| `campus-backend.service` | 后端 Spring Boot 的 systemd 服务单元 |
| `campus.env.example` | 后端环境变量模板（复制到 `/etc/campus/campus.env`） |
| `nginx-campus.conf` | Nginx 站点配置：托管前端 `dist/` 并把 `/api` 反代到后端 8080 |

完整的分步部署流程、依赖安装与排错见仓库根目录的 [`../REPORT.md`](../REPORT.md) 与 [`../BUILD.md`](../BUILD.md)。

## 相关启动/停止脚本（`scripts/`）

| 脚本 | 作用 |
| --- | --- |
| `start-nginx.sh` | 启动 Nginx；`ENABLE_SITE=1` 可顺带把本目录的 `nginx-campus.conf` 装成站点 |
| `start-all.sh` | 一键启动后端 + 前端 + Nginx（`FRONTEND_MODE=auto\|dev\|prod`） |
| `stop-backend.sh` / `stop-frontend.sh` / `stop-nginx.sh` | 分别停止后端 / 前端 / Nginx |

> 若后端由 systemd 托管，请用 `sudo systemctl stop campus-backend` 停止，不要用 `stop-backend.sh`，
> 否则 systemd 会按 `Restart=on-failure` 自动拉起进程。


## 速览

```bash
# 1) 构建产物
scripts/build.sh

# 2) 放置文件
sudo mkdir -p /opt/campus/frontend
sudo cp backend/target/campus-info-platform-backend-1.0.0.jar /opt/campus/
sudo cp -r frontend/dist /opt/campus/frontend/

# 3) 后端服务
sudo useradd --system --home /opt/campus --shell /usr/sbin/nologin campus || true
sudo mkdir -p /etc/campus
sudo cp deploy/campus.env.example /etc/campus/campus.env
sudo cp deploy/campus-backend.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now campus-backend

# 4) 前端 + 反向代理
sudo apt install -y nginx
sudo cp deploy/nginx-campus.conf /etc/nginx/sites-available/campus
sudo ln -sf /etc/nginx/sites-available/campus /etc/nginx/sites-enabled/campus
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```
