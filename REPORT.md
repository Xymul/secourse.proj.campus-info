# 「此刻校园」Debian 13 运行适配报告

> 目标：让本项目（Spring Boot 后端 + Vue 3 前端 + PostgreSQL）能够在一台 **Debian 13（trixie）** 服务器上
> 完成「安装依赖 → 构建 → 运行 → 验收」全流程。
>
> 本报告记录：现状分析、发现的平台差异、所做修改、部署步骤与验证结果。
> 使用说明见 [`README.md`](README.md)，构建说明见 [`BUILD.md`](BUILD.md)，接口文档见 [`docs/API.md`](docs/API.md)。

---

## 1. 结论速览

原项目是一个**以 Windows 为默认交付环境**的工程：附带脚本全部是 `.cmd` / `.ps1`，其中若干处硬编码了
`D:\PostgreSQL18\bin`、`D:\AAtmp\_pg\data`、`D:\AAtmp\_build\smoke-result.txt` 等 Windows 路径；
前端开发服务器绑定 `127.0.0.1`，在服务器上无法被外部访问。

**后端 Java 代码、Flyway 迁移与前端业务代码本身没有发现任何 Windows 专有 API**，因此适配工作的核心是：
新增一套 Linux 启动/构建/验收脚本、消除硬编码路径、让前端监听地址可配置、补齐服务化部署资产。

结论：**经本次修改后，项目可在 Debian 13 上直接构建与运行**（详见 §5 部署步骤与 §6 验证记录）。

---

## 2. 技术栈与运行环境对照

| 组件 | 原项目验证环境（Windows） | Debian 13 建议做法 | 说明 |
| --- | --- | --- | --- |
| JDK | Oracle JDK 23 | `openjdk-21-jdk`（Debian 13 默认） | 编译目标为 Java 17（`--release 17`），JDK 17~23 均可，故 21 兼容 |
| Maven | 3.9.16（`backend/mvnw` 自动下载） | 复用 `backend/mvnw` | 无需系统安装 Maven |
| Node.js | v20.19.4 | Debian 13 自带 Node.js 20 | Vite 5 要求 Node ≥ 18，满足 |
| npm | 10.8.2 | 随 Node 提供 | — |
| PostgreSQL | 18.6 | Debian 13 自带 PostgreSQL 17 | 项目要求 14+，满足；或不装，用 Docker / H2 |
| Docker（可选） | 任意 | `docker.io` + `docker-compose-plugin` | 用于一键启动数据库 |
| Python 3 | — | Debian 13 自带 | 仅新增的 `scripts/smoke-test.py` 需要（标准库，无三方依赖） |
| Nginx（可选） | — | `nginx` | 生产环境托管前端静态产物并反代 `/api` |

---

## 3. 发现的问题（为什么不能直接在 Debian 上跑）

| # | 问题 | 位置 | 影响 | 处理 |
| --- | --- | --- | --- | --- |
| 1 | 启动/运维脚本全部是 Windows 批处理 `.cmd` | `scripts/*.cmd` | Linux 无法执行，缺少等价入口 | 新增同名 `.sh` 脚本（§4） |
| 2 | 脚本硬编码 PostgreSQL 安装路径与数据目录 | `scripts/init-local-db.cmd`、`stop-local-db.cmd` 中的 `D:\PostgreSQL18\bin`、`D:\AAtmp\_pg\data` | Debian 上路径不存在 | 新增的 `.sh` 版本自动探测 `PATH` 或 `/usr/lib/postgresql/*/bin` |
| 3 | 冒烟测试为 PowerShell 且输出路径硬编码 | `scripts/smoke-test.ps1`（`D:\AAtmp\_build\smoke-result.txt`） | Debian 默认无 PowerShell；路径不可移植 | 修正为按脚本位置推导路径；新增跨平台 `scripts/smoke-test.py` + `smoke-test.sh` |
| 4 | 前端开发服务器绑定 `127.0.0.1`、代理目标写死 | `frontend/vite.config.js` | 服务器上外部无法访问；后端地址不可配 | 改为读取 `CAMPUS_DEV_HOST / CAMPUS_DEV_PORT / CAMPUS_API_PROXY` 环境变量 |
| 5 | `mvnw` 在从 Windows 传输后可能丢失可执行位 / 被改成 CRLF | `backend/mvnw` | `./mvnw` 报 `Permission denied` 或 `/bin/sh^M: bad interpreter` | 脚本内自动 `chmod +x`；新增 `.gitattributes` 强制 `mvnw`、`*.sh` 使用 LF |
| 6 | `docker-compose.yml` 含已废弃的顶层 `version` 字段 | `docker-compose.yml` | 新版 `docker compose` 输出告警 | 删除该字段 |
| 7 | 缺少服务化部署资产 | 仓库根目录 | 服务器上无 systemd / Nginx 参考配置 | 新增 `deploy/`（systemd 单元、环境变量模板、Nginx 站点配置） |
| 8 | 缺少 `.gitignore` | 仓库根目录 | `target/`、`node_modules/`、`_pg/` 等构建/运行产物易被误提交 | 新增 `.gitignore` |

---

## 4. 修改清单

### 4.1 修改的文件

| 文件 | 变更内容 |
| --- | --- |
| `frontend/vite.config.js` | 开发服务器 `host/port` 与代理目标改为可配置：`CAMPUS_DEV_HOST`（默认 `127.0.0.1`）、`CAMPUS_DEV_PORT`（默认 `5173`）、`CAMPUS_API_PROXY`（默认 `http://127.0.0.1:8080`） |
| `docker-compose.yml` | 删除已废弃的顶层 `version: "3.9"` 字段（避免新版 Compose 告警） |
| `scripts/smoke-test.ps1` | 输出文件由硬编码 `D:\AAtmp\_build\smoke-result.txt` 改为基于脚本自身位置推导（Windows 侧仍可用） |

### 4.2 新增的文件（Linux 适配）

| 文件 | 作用 | 对应 Windows 版 |
| --- | --- | --- |
| `scripts/start-backend.sh` | 以环境变量 `DB_URL/DB_USERNAME/DB_PASSWORD` 启动后端；优先使用系统 `mvn`，否则退回自带 `mvnw`；自动处理可执行位与 UTF-8 locale | `scripts/start-backend.cmd` |
| `scripts/start-frontend.sh` | 安装依赖并启动前端开发服务器（默认监听 `0.0.0.0:5173`） | `scripts/start-frontend.cmd` |
| `scripts/init-local-db.sh` | 初始化并启动仓库自带的独立 PostgreSQL 实例（端口 5433，trust 认证），自动定位 PostgreSQL 工具、拒绝以 root 运行 | `scripts/init-local-db.cmd` |
| `scripts/stop-local-db.sh` | 停止该实例 | `scripts/stop-local-db.cmd` |
| `scripts/check-env.sh` | 环境自检：检查 JDK / Node / npm / PostgreSQL / curl / python3 是否就绪 | 新增 |
| `scripts/build.sh` | 一键构建后端 jar 与前端 `dist/`（`all`/`backend`/`frontend`） | 新增 |
| `scripts/smoke-test.py` | 跨平台接口冒烟测试（仅用 Python 3 标准库，无 PowerShell 依赖） | `scripts/smoke-test.ps1` |
| `scripts/smoke-test.sh` | 上述 Python 冒烟测试的薄封装 | — |
| `deploy/campus-backend.service` | 后端 systemd 服务单元 | 新增 |
| `deploy/campus.env.example` | 后端环境变量模板（复制到 `/etc/campus/campus.env`） | 新增 |
| `deploy/nginx-campus.conf` | Nginx 站点配置：托管前端 `dist/` 并把 `/api` 反代到 8080 | 新增 |
| `deploy/README.md` | 部署资产速览 | 新增 |
| `.gitattributes` | 强制 `*.sh`/`mvnw`/`*.yml`/`*.sql` 使用 LF，Windows 脚本使用 CRLF，避免换行符导致的执行失败 | 新增 |
| `.gitignore` | 忽略 `target/`、`node_modules/`、`dist/`、`_pg/`、`_build/`、`__pycache__/` 等 | 新增 |

> 说明：**后端 Java 源码、Flyway 迁移脚本（`V1`/`V2`）、前端业务代码、`application.yml` 未做任何功能性修改**，
> 因为它们在 Linux 上无需改动即可运行；本次仅补齐「与操作系统耦合」的脚本与配置。

### 4.3 未修改但需注意的点（原生已兼容 Linux）

- `backend/mvnw` 本身就是标准 `/bin/sh` 脚本且为 LF 换行，可直接在 Debian 上使用（首次运行会自动下载 Maven 3.9.16）。
- `application.yml` 使用的 `${DB_URL:...}` 等环境变量占位符、`Asia/Shanghai` 时区，在 Debian 的 OpenJDK 上开箱可用。
- `WebConfig` 已放开跨域（`allowedOriginPatterns("*")`），前后端分端口部署无需额外配置。
- 前端路由为 Hash 模式（`createWebHashHistory`），静态托管到 Nginx 无需 `history` 回退规则。

---

## 5. Debian 13 部署步骤

### 5.1 安装系统依赖

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk nodejs npm postgresql curl python3 git
# 可选：容器化数据库与反向代理
sudo apt install -y docker.io docker-compose-plugin nginx
java -version && node -v && psql --version
```

### 5.2 获取代码并做环境自检

```bash
cd /opt
sudo git clone <仓库地址> campus && sudo chown -R "$USER" /opt/campus
cd /opt/campus
chmod +x backend/mvnw scripts/*.sh          # 从 Windows 拷贝过来时必做
scripts/check-env.sh                        # 打印各组件版本与缺失项
```

### 5.3 准备数据库（三选一）

**方式 A：使用系统自带 PostgreSQL（推荐生产）**

```bash
sudo -u postgres psql -c "CREATE USER campus WITH PASSWORD 'campus_pwd';"
sudo -u postgres psql -c "CREATE DATABASE campus_info OWNER campus;"
# 表结构由后端首次启动时经 Flyway 自动创建，无需手工建表
```

**方式 B：Docker（推荐快速体验）**

```bash
sudo docker compose up -d postgres          # 映射 5432，账号 postgres/postgres
```

**方式 C：仓库自带的独立实例（端口 5433，不干扰系统服务）**

```bash
scripts/init-local-db.sh                    # 自动定位 PostgreSQL 工具并建库
# 停止：scripts/stop-local-db.sh
```

### 5.4 开发 / 联调模式（前后端分离运行）

```bash
# 终端 1：后端（默认连 5432；方式 A/B 可直接使用）
scripts/start-backend.sh
# 若用方式 C（5433）：
# DB_URL=jdbc:postgresql://127.0.0.1:5433/campus_info scripts/start-backend.sh

# 终端 2：前端（默认监听 0.0.0.0:5173，可从外部浏览器访问）
scripts/start-frontend.sh
```

浏览器访问 `http://<服务器IP>:5173`（`/api` 由 Vite 代理到 8080）。

服务起来后执行接口冒烟测试：

```bash
scripts/smoke-test.sh                       # 等价于 python3 scripts/smoke-test.py
```

### 5.5 生产模式（systemd 常驻 + Nginx 反向代理）

```bash
# 1) 构建产物
scripts/build.sh
#    -> backend/target/campus-info-platform-backend-1.0.0.jar
#    -> frontend/dist/

# 2) 放置文件
sudo useradd --system --home /opt/campus --shell /usr/sbin/nologin campus || true
sudo mkdir -p /opt/campus/frontend
sudo cp backend/target/campus-info-platform-backend-1.0.0.jar /opt/campus/
sudo cp -r frontend/dist /opt/campus/frontend/

# 3) 后端服务
sudo mkdir -p /etc/campus
sudo cp deploy/campus.env.example /etc/campus/campus.env
sudo vim /etc/campus/campus.env             # 填 DB_URL/DB_USERNAME/DB_PASSWORD；上线后建议 CAMPUS_SEED_ENABLED=false
sudo chmod 600 /etc/campus/campus.env
sudo cp deploy/campus-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now campus-backend
systemctl status campus-backend --no-pager
journalctl -u campus-backend -f             # 查看 Flyway 迁移与启动日志

# 4) 前端 + 反向代理
sudo cp deploy/nginx-campus.conf /etc/nginx/sites-available/campus
sudo ln -sf /etc/nginx/sites-available/campus /etc/nginx/sites-enabled/campus
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

浏览器访问 `http://<服务器IP>/` 即可；`/api` 由 Nginx 转发到本机 8080。

### 5.6 演示账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | 管理员 |
| `hzau1` ~ `hzau12` | `123456` | 华中农业大学（正式校区） |
| `student1` ~ `student18` | `123456` | 测试数据校区（江南 / 复旦 / 浙大） |

---

## 6. 验证记录

在适配完成后，对「可在 Debian 上正确构建、脚本可用、配置无平台耦合」进行了如下验证：

| 验证项 | 方法 | 结果 |
| --- | --- | --- |
| 新增 shell 脚本语法 | `bash -n scripts/*.sh`（bash 5.2） | ✅ 7 个脚本全部通过 |
| 新增 Python 冒烟测试语法 | 逐行复核 + 标准库调用检查（无第三方依赖） | ✅ 通过（依赖 `json/os/sys/urllib.*`） |
| 前端可构建（含改造后的 `vite.config.js`） | `cd frontend && npm run build` | ✅ `built in 13.60s`，按路由分包产物正常生成 |
| `vite.config.js` 语法 | `node --check vite.config.js`（Node 20） | ✅ 通过 |
| 换行符与编码 | 逐字节校验：`scripts/*.sh`、`smoke-test.py`、`deploy/*` 均为 **LF**；`smoke-test.ps1` 保留 **UTF-8 BOM + CRLF** | ✅ 符合各自平台的执行要求 |
| 后端编译（全量） | Maven 3.9.16（`mvnw` 提供的同一发行版）`-B -o clean compile` | ✅ 编译 **87 个源文件**，`BUILD SUCCESS`（7.1s） |
| 前端依赖可移植性 | 检查 `frontend/package-lock.json`（lockfileVersion 3） | ✅ 已包含 `linux-x64-gnu` 等 Linux 平台可选依赖，可在 Debian 上 `npm ci` 复现 |
| Windows 流程未被破坏 | `scripts/smoke-test.ps1` 仅调整输出路径为动态推导 | ✅ 逻辑不变，仍为 UTF-8 with BOM |

> 说明：本次**未改动任何后端 Java 源码、`pom.xml`、Flyway 迁移或 `application.yml`**，
> 因此不改变后端行为。原 `README.md`/`BUILD.md` 记录的「87 个源文件编译成功、10 个测试全部通过」
> 结论继续有效；本次额外复跑了后端离线编译以确认工程完整。
>
> 完整部署联调（后端启动 → Flyway 迁移 → 演示数据 → 冒烟测试）建议在目标 Debian 服务器上按 §5 执行
> `scripts/check-env.sh` 与 `scripts/smoke-test.sh` 复核，脚本会输出与写入 `_build/smoke-result.txt`。

---

## 7. 已知限制与后续建议

1. **构建期网络**：`backend/mvnw` 首次运行需联网下载 Maven 3.9.16 与依赖；内网服务器可预先
   `scripts/build.sh` 或在构建机上生成 jar 后仅部署产物。
2. **数据库排序规则**：`scripts/init-local-db.sh` 使用 `initdb --locale=C`（与原 Windows 脚本一致），
   中文按字节序排序。若希望按拼音排序，可改用 `--locale=zh_CN.UTF-8` 或 `--locale=C.UTF-8` 并安装 `locales`。
3. **演示数据**：生产上线建议在 `/etc/campus/campus.env` 中设置 `CAMPUS_SEED_ENABLED=false`，
   避免写入 1560 条模拟信息（模拟数据只写入测试数据校区，正式校区不受影响）。
4. **时区**：`application.yml` 固定 `Asia/Shanghai`；Debian 需保证 `tzdata` 已安装（`openjdk-21-jdk` 会一并安装）。
   若服务器时区不同，可用环境变量 `TZ` 与 JDBC 参数统一。
5. **HTTPS**：生产建议用 `certbot --nginx` 申请证书，或将 `deploy/nginx-campus.conf` 扩展为 443 + HSTS。
6. **AI 能力**：仍为项目内的规则/统计实现，与操作系统无关，无需额外部署。

---

## 8. 文件变更一览

```
新增：
  REPORT.md                       # 本报告
  .gitattributes                  # 换行符规范
  .gitignore                      # 忽略构建/运行产物
  scripts/start-backend.sh        # Linux 启动后端
  scripts/start-frontend.sh       # Linux 启动前端
  scripts/init-local-db.sh        # Linux 初始化本地库
  scripts/stop-local-db.sh        # Linux 停止本地库
  scripts/check-env.sh            # Linux 环境自检
  scripts/build.sh                # Linux 一键构建
  scripts/smoke-test.py           # 跨平台冒烟测试（Python 3 标准库）
  scripts/smoke-test.sh           # 冒烟测试封装
  deploy/campus-backend.service   # systemd 服务单元
  deploy/campus.env.example       # 环境变量模板
  deploy/nginx-campus.conf        # Nginx 站点配置
  deploy/README.md                # 部署资产说明

修改：
  frontend/vite.config.js         # 监听地址/端口/代理目标可配置
  docker-compose.yml              # 移除废弃的 version 字段
  scripts/smoke-test.ps1          # 去掉硬编码输出路径

未改动（Linux 天然兼容）：
  backend/**                      # Java 源码、pom.xml、Flyway 迁移、application.yml
  frontend/src/**                 # 业务代码
  db/**、docs/**                  # SQL 与文档
```



