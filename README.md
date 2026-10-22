# 此刻校园 · 校园信息共享与生活地图系统

> 依据 `docs/` 下的《此刻校园 软件需求规格说明书》与《校园信息地图开发步骤详解》实现的可运行系统。
> 后端 Spring Boot + PostgreSQL（事务处理），前端 Vue 3 + OpenLayers（地图展现），
> **AI 智能搜索与 AI 辅助发布暂用自实现的分词/意图解析/相关性打分算法**，后续可平滑替换为大模型或向量检索。
>
> 文档导航：[`REPORT.md`](REPORT.md) Debian 13 部署适配报告 ｜ [`BUILD.md`](BUILD.md) 构建说明 ｜
> [`docs/API.md`](docs/API.md) 接口文档 ｜
> [`docs/数据库设计说明.md`](docs/数据库设计说明.md) 数据模型 ｜
> [`docs/需求实现对照与验收.md`](docs/需求实现对照与验收.md) 验收对照 ｜
> [`docs/需求文档摘录/`](docs/需求文档摘录) 需求原文摘录

---

> 由于近期的安全漏洞，服务器暂停维护

## 1. 快速开始

### 1.1 环境要求

| 组件           | 版本建议                             | 说明                   |
| -------------- | ------------------------------------ | ---------------------- |
| JDK            | 17+（本机验证于 JDK 23）             | 后端编译目标为 Java 17 |
| Maven          | 3.9+（已随附 `backend/mvnw` 包装器） | 无需单独安装           |
| Node.js        | 18+（本机验证于 Node 20）            | 前端构建               |
| PostgreSQL     | 14+（本机验证于 18）                 | 主数据库；PostGIS 可选 |
| Docker（可选） | 任意                                 | 用于一键启动数据库     |

### 1.2 准备数据库（三选一）

**方式 A：Docker（推荐）**

```bash
docker compose up -d postgres
```

**方式 B：使用本机已有 PostgreSQL**：创建数据库 `campus_info` 即可，表结构由 Flyway 自动创建。

**方式 C：本仓库自带的独立实例脚本（不干扰已有服务，端口 5433）**

```bat
scripts\init-local-db.cmd      :: 初始化数据目录并启动，默认 trust 认证
scripts\stop-local-db.cmd      :: 停止实例
```

### 1.3 启动后端

```bat
scripts\start-backend.cmd
:: 等价于：
:: set DB_URL=jdbc:postgresql://127.0.0.1:5432/campus_info
:: set DB_USERNAME=postgres
:: set DB_PASSWORD=postgres
:: cd backend && mvnw spring-boot:run
```

启动后：

- Flyway 自动执行 `V1__init_schema.sql`（表结构）与 `V2__school_real_campus.sql`（正式/测试校区）迁移，
  Hibernate 仅做 `validate` 校验，保证实体与表结构一致；
- `DataSeeder` 幂等初始化：**5 个校区**（华中农业大学 2 个正式校区 + 江南/复旦/浙大 3 个测试数据校区）、
  6 个分类、**90 个校园地点**（每校区 18 个）、**31 个账号**，
  以及 **1560 条模拟校园信息**（平均写入 3 个测试数据校区，正式校区保持为空）；
  详见 §1.6；
- 服务地址 <http://127.0.0.1:8080>。

**不想安装 PostgreSQL？** 可用内存库快速体验：

```bat
cd backend
set SPRING_PROFILES_ACTIVE=h2
mvnw spring-boot:run
```

### 1.4 启动前端

```bat
scripts\start-frontend.cmd      :: 等价于 cd frontend && npm install && npm run dev
```

访问 <http://127.0.0.1:5173>（开发环境下 `/api` 由 Vite 代理到 8080，无需处理跨域）。

生产构建：`cd frontend && npm run build`，产物在 `frontend/dist`，可用 Nginx 托管并反向代理 `/api`。

### 1.5 演示账号

| 账号                     | 密码       | 角色     | 说明                                                                           |
| ------------------------ | ---------- | -------- | ------------------------------------------------------------------------------ |
| `admin`                  | `admin123` | 管理员   | 可进入「管理后台」                                                             |
| `hzau1` ~ `hzau12`       | `123456`   | 普通用户 | **华中农业大学**（正式校区）：hzau1~6 狮子山主校区、hzau7~12 襄阳校区          |
| `student1` ~ `student18` | `123456`   | 普通用户 | 测试数据校区：student1~6 江南大学、student7~12 复旦大学、student13~18 浙江大学 |

### 1.6 校区（多租户）

右上角「校区」下拉框按 `sort_order` 排序并分组展示：**正式校区** 在上，**测试数据** 在下。

| 排序 | 校区                         | code             | 坐标（WGS-84）        | 是否测试数据 | 说明                                                             |
| ---- | ---------------------------- | ---------------- | --------------------- | ------------ | ---------------------------------------------------------------- |
| 1    | 华中农业大学（狮子山主校区） | `hzau-main`      | 30.473990, 114.348398 | 否           | 正式校区，坐标为 OSM/Photon 地理编码结果（武汉市洪山区狮子山街） |
| 2    | 华中农业大学（襄阳校区）     | `hzau-xiangyang` | 31.999918, 112.276105 | 否           | 正式校区（襄阳市襄州区蔡州路）                                   |
| 10   | 江南大学（蠡湖校区）         | `jiangnan`       | 31.485600, 120.265300 | **是**       | 演示/测试数据校区                                                |
| 11   | 复旦大学（邯郸校区）         | `fudan`          | 31.298900, 121.499800 | **是**       | 演示/测试数据校区                                                |
| 12   | 浙江大学（紫金港校区）       | `zju`            | 30.308400, 120.086300 | **是**       | 演示/测试数据校区                                                |

规则说明：

1. **模拟信息只写入测试数据校区**：启动时生成的 1560 条模拟校园信息平均分布到 3 个测试数据校区，
   正式校区（华农）保持为空，由真实用户发布内容；
2. 前端在「测试数据」校区下会显示橙色 `测试数据` 标记，切换时也会提示；正式校区显示绿色 `正式校区` 标记；
3. 每个校区都会自动生成 18 个通用校园地点（图书馆、食堂、田径场、快递驿站…）与 6 个普通用户账号，
   地点可在后台「地点核验」中按实际情况改名与调整坐标；
4. 新增/调整校区只需修改 `DataSeeder.SCHOOLS`（code、名称、坐标、`testData`、`sortOrder`），重启即生效；
5. 老浏览器中保存的旧校区选择会在本次升级后**一次性重置**为第一个正式校区（之后仍会记住用户的手动选择）。

### 1.7 在 Linux / Debian 13 服务器上运行

仓库同时提供与 Windows 脚本一一对应的 Linux（Debian 13）脚本：

| Linux 脚本                                               | 作用                                                                                               | 对应 Windows 版                            |
| -------------------------------------------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| `scripts/build.sh [all\|backend\|frontend]`              | 一键构建后端可执行 jar 与前端 `dist/`                                                              | —                                          |
| `scripts/start-all.sh`                                   | 一键启动后端 + 前端 + Nginx（`FRONTEND_MODE=auto\|dev\|prod`、`BUILD=1`、`NO_NGINX=1`）            | —                                          |
| `scripts/start-backend.sh` / `scripts/start-frontend.sh` | 启动后端 / 前端                                                                                    | `start-backend.cmd` / `start-frontend.cmd` |
| `scripts/start-nginx.sh`                                 | 启动 Nginx（`ENABLE_SITE=1` 可一并启用 `deploy/nginx-campus.conf` 站点，`RELOAD_ONLY=1` 仅热加载） | —                                          |
| `scripts/stop-backend.sh` / `scripts/stop-frontend.sh`   | 停止后端（默认端口 8080）/ 前端（默认端口 5173）                                                   | —                                          |
| `scripts/stop-nginx.sh`                                  | 停止 Nginx 并回收 80 端口（`CHECK_PORT=0` 跳过端口兜底）                                           | —                                          |
| `scripts/init-local-db.sh` / `scripts/stop-local-db.sh`  | 初始化 / 停止仓库自带的 PostgreSQL 实例（端口 5433）                                               | `init-local-db.cmd` / `stop-local-db.cmd`  |
| `scripts/check-env.sh`                                   | 环境自检（JDK / Node / npm / PostgreSQL / curl / python3）                                         | —                                          |
| `scripts/smoke-test.sh`                                  | 接口冒烟测试（等价于 `smoke-test.ps1`，实现见 `smoke-test.py`）                                    | `smoke-test.ps1`                           |

首次使用请先 `chmod +x backend/mvnw scripts/*.sh`；systemd 常驻与 Nginx 反向代理模板见 `deploy/`。
完整的适配分析、依赖安装与部署步骤见 [`REPORT.md`](REPORT.md)。

> 注意：后端若由 systemd（`campus-backend.service`）托管，请使用 `sudo systemctl stop campus-backend` 停止，
> 不要使用 `stop-backend.sh`——否则 systemd 会按 `Restart=on-failure` 自动拉起进程。

---

## 2. 目录结构

```
D:/AAtmp
├─ README.md                      # 使用说明（功能、账号、接口、FAQ）
├─ BUILD.md                       # ★ 构建说明（环境、命令、配置要点、排错）
├─ backend/                      # Spring Boot 后端（Java 17 + Spring Data JPA + Flyway）
│   └─ src/main/java/org/secourse/hzau/campus
│       ├─ entity/               # 13 张业务表实体（含枚举）
│       ├─ repository/           # Spring Data 仓储（含空间查询、聚合统计）
│       ├─ service/              # 业务服务（均为事务边界所在）
│       ├─ search/               # ★ 自实现的搜索/意图解析/AI 辅助发布算法
│       ├─ web/                  # REST 控制器（含 admin 子包）
│       ├─ security/             # 令牌会话、登录与权限拦截器
│       ├─ scheduler/            # 生命周期定时任务
│       └─ seed/                 # 校区/分类/地点/账号/模拟信息初始化（1500+ 条）
├─ frontend/                     # Vue 3 + Vite + OpenLayers + Element Plus
│   └─ src/{views,components,api,store,router,utils}
├─ db/                           # 可选 SQL 脚本（PostGIS 升级 / 纯 SQL 造数）
├─ docs/                         # 接口文档、数据库设计、算法说明、需求对照、需求原文摘录
├─ scripts/                      # 启动脚本（数据库 / 后端 / 前端）
└─ _build/                       # 本地验证日志与临时脚本（可删除）
```

---

## 3. 功能实现与需求对照

| 需求编号 | 功能域             | 实现位置                                                                              | 说明                                                                                     |
| -------- | ------------------ | ------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| F1       | 校园地图与信息浏览 | `PostService#mapNodes/nearby/locationCluster`、`PostSpatialRepository`、`MapView.vue` | 视野内加载（bbox 查询）、分类筛选、同地点聚合、周边 X 公里检索、地图打点与弹窗           |
| F2       | 信息搜索           | `SearchService`、`search/` 包、`SearchView.vue`                                       | 普通关键词搜索（数据库 LIKE）+ AI 智能搜索（分词→意图解析→相关性排序→匹配原因→条件放宽） |
| F3       | 信息发布           | `PostService#publish/update`、`AiPublishAdvisor`、`PublishView.vue`                   | 标题/正文/图片/地点/分类/有效期；AI 建议分类·标签·有效期，用户可修改；命中敏感词转待审核 |
| F4       | 互动与协同验证     | `InteractionService`、`PostDetailView.vue`                                            | 评论、点赞、证实/证伪（状态互斥 + 唯一约束 + 防刷冷却）、失物招领认领、联系方式脱敏      |
| F5       | 信息治理           | `GovernanceService`、`PostLifecycleScheduler`                                         | 举报（阈值自动转待审核）、内容审核、过期自动治理、任务记录与操作日志                     |
| F6       | 后台运营管理       | `AdminService`、`web/admin/*`、`views/admin/*`                                        | 仪表盘、内容审核、举报管理、用户管理、地点核验、分类管理、任务记录、操作日志、数据分析   |

页面清单 —— 用户端：首页、校园地图、信息搜索、发布信息、信息详情、登录、注册、个人中心；
管理端（Element Plus）：仪表盘、内容审核、举报管理、用户管理、地点核验、分类管理、任务记录/操作日志、数据分析。

---

## 4. 后端设计要点

### 4.1 技术选型

- Spring Boot 3.5 + Spring MVC + Spring Data JPA + Validation + Flyway + PostgreSQL；
- 会话：轻量令牌（`auth_token` 表）+ 拦截器鉴权，`/api/admin/**` 需管理员角色；
- 表结构由 Flyway 管理（`V1__init_schema.sql`），Hibernate `ddl-auto=validate` 保证实体与库结构一致；
- 空间能力：默认使用 `double precision(lat/lng)` + SQL 内 Haversine 公式，**不依赖 PostGIS 即可运行**；
  需要更高性能时执行 `db/optional_postgis.sql` 启用 geography + GIST 索引。

### 4.2 事务处理（重点）

| 场景                     | 方法                                                 | 事务与并发设计                                                                                                                                                        |
| ------------------------ | ---------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 发布信息                 | `PostService#publish`                                | `@Transactional(rollbackFor = Exception.class)`：有效期计算、敏感词判定、落库、写操作日志同一事务，任一步失败整体回滚                                                 |
| 编辑 / 下架 / 标记已解决 | `PostService#update/remove/resolve`                  | 事务内校验「作者或管理员」，状态与日志同时提交                                                                                                                        |
| 协同验证                 | `InteractionService#verify`                          | ① `verification(post_id,user_id)` 唯一约束保证状态互斥；② 帖子 `@Version` 乐观锁避免计数丢失；③ 冲突时用 `TransactionTemplate` 重试整个事务（事务边界位于重试循环内） |
| 评论 / 点赞              | `InteractionService#addComment/like`                 | 明细与帖子计数（评论数 / 点赞数）在同一事务更新                                                                                                                       |
| 失物招领认领             | `InteractionService#claim/handleClaim`               | 认领记录与帖子「已解决」状态同一事务，避免「认领通过但信息仍在流转」                                                                                                  |
| 举报                     | `GovernanceService#report`                           | 举报明细、举报计数、阈值触发转待审核在同一事务                                                                                                                        |
| 举报处理 / 内容审核      | `GovernanceService#handleReport/audit`               | 举报状态 + 帖子状态 + 操作日志同一事务                                                                                                                                |
| 过期治理定时任务         | `GovernanceService#startTask/expirePosts/finishTask` | 三段 `REQUIRES_NEW` 独立事务：即使批量处理失败，任务轨迹（RUNNING → FAILED）依然完整可查                                                                              |
| 只读查询                 | 各服务 `@Transactional(readOnly = true)`             | 只读事务 + `EntityGraph` 预取，避免 N+1；`spring.jpa.open-in-view=false`                                                                                              |

### 4.3 数据模型

13 张表：`school`、`app_user`、`category`、`campus_location`、`post`、`comment`、`verification`、
`post_like`、`report`、`claim`、`operation_log`、`task_record`、`auth_token`。
详见 [`docs/数据库设计说明.md`](docs/数据库设计说明.md)。

---

## 5. AI 智能搜索（自实现算法）

> 需求约定：本阶段不接入大模型，AI 搜索以「中文分词 + 意图解析 + 统计相关性排序」的自实现算法提供，
> 后续可平滑替换为真正的模型 / 向量检索。算法完全可解释、可测试、无外部依赖。

### 5.1 处理流程

```
用户自然语言
   │
   ├─ ① 中文分词（ChineseTokenizer）        领域词典最长匹配 + bigram + 单字，三级粒度权重
   ├─ ② 意图解析（SearchIntentParser）      分类 / 地点 / 时间 / 距离 / 价格 / 关键词 六类条件
   ├─ ③ 条件检索（SimpleSearchEngine）      条件硬过滤 → BM25 变体打分排序
   ├─ ④ 可解释输出                          每条结果附「匹配原因」，整体附「意图说明」
   └─ ⑤ 健壮降级                            周边→时间→地点→分类→关键词逐级放宽，最终热门推荐
```

### 5.2 打分公式（BM25 变体）

```
score(doc) = Σ_term  idf(term) · (tfw · (k1+1)) / (tfw + k1 · (1 - b + b · len/avgLen)) · termWeight
             + 意图加成 + 质量加成

tfw   = 3.0·tf(标题) + 2.0·tf(标签) + 1.6·tf(地点) + 1.5·tf(分类) + 1.0·tf(正文)   （字段加权词频）
idf   = ln(1 + (N - df + 0.5) / (df + 0.5))                                        （候选集内文档频率）
termWeight：词典词 1.0 / bigram 0.8 / 单字 0.5                                       （粒度权重）

意图加成：分类命中 +3.0、地点命中 +2.4、时间窗口命中 +1.4、预算符合 +1.0、
          距离衰减 +2.0·(1 - d/radius)、标题完整包含原句 +3.5
质量加成：新鲜度 +1.5·0.5^(ageDays/半衰期)、可信度 +1.2·证实占比（或按证伪占比扣分）、
          互动热度 + min(1, 阅读/200)·0.5 + min(1, 评论/20)·0.6 + min(1, 点赞/20)·0.5
```

排序结果按最高分归一化为「相关度百分比」，前端以进度条 + 匹配原因标签展示。

### 5.3 意图识别词典（节选）

| 意图     | 关键词示例                                             |
| -------- | ------------------------------------------------------ |
| 失物招领 | 失物、丢失、丢了、捡到、招领、认领、钱包、钥匙、校园卡 |
| 二手交易 | 二手、出售、转让、求购、闲置、价格、多少钱、元         |
| 组队交友 | 组队、招人、缺人、搭子、拼车、约球、一起               |
| 校园活动 | 活动、讲座、比赛、报名、宣讲、招新、竞赛               |
| 分享吐槽 | 吐槽、避雷、安利、评价、分享、体验、涨价               |
| 时间     | 今天 / 昨天 / 最近 / 本周 / 周末 / 本月 → 近 N 天窗口  |
| 距离     | 附近、周边、旁边 + 「3公里」等显式半径                 |
| 价格     | 「300元」「300块」→ 预算上限                           |

### 5.4 AI 辅助发布（`AiPublishAdvisor`）

- **分类建议**：复用同一份意图词典对「标题+正文」分类，给出置信度与命中词依据；
- **标签建议**：分词关键词（按粒度权重排序）+ 分类标签词库 + 识别到的地点，去重取前 5；
- **有效期建议**：分类默认有效期，命中「急/今天/限时」→ 缩至 3 天，命中「长期有效」→ 扩至 30 天；
- **合规提示**：敏感词表命中（如「代考/刷单/校园贷」）→ 风险等级 + 提示，HIGH 级别发布后直接转待审核；
- **隐私提示**：正文中检测到手机号 / QQ / 微信 → 建议改填「联系方式」字段，展示时按权限脱敏。

所有建议均为「可修改的默认值」，用户确认后再提交，符合需求中「降低发布门槛但不替用户做决定」。

---

## 6. 前端设计要点

- **地图（OpenLayers 10）**：OSM 底图；`moveend` 时按当前视野 bbox 拉取信息节点；
  分类筛选 chips；同地点信息自动聚合为带数量角标的聚合点；点击弹窗可查看详情或展开同地点列表；
  支持浏览器定位与「周边 X 公里」半径滑杆，点击地图任意位置即可检索该点周边信息。
- **多底图与网络自适应**（解决「只看到信息点、看不到底图」）：
  - 内置 8 个底图源，下拉框可随时切换：「OSM 标准地图（德国镜像）」「高德地图（中文标注）」
    「高德卫星影像」「ESRI 全球卫星影像」「ESRI 街道地图」「OSM HOT 镜像」「OSM 官方」「Carto 简洁」；
  - 启动时优先使用上次选择；若该源不可达（瓦片连续加载失败 3 次）会**自动探测并回退**到可用源，
    也可点击「检测底图」手动探测，选择结果保存在 `localStorage.campus_basemap`；
  - 高德等 **GCJ-02** 底图会自动做坐标纠偏（`src/utils/geo.js` 的 WGS-84 ↔ GCJ-02 正反解），
    显示时转 GCJ-02、地图选点后转回 WGS-84 再提交，保证「信息点与底图对齐」且入库坐标始终是标准 GPS 坐标。
- **地图选点**：发布页内嵌简易地图（`MiniMapPicker`），与主地图共享同一底图与纠偏规则，
  点击选坐标或一键选中校园地点。
- **搜索页**：普通 / AI 双模式，AI 模式展示意图解析结果（分类/地点/时间/距离/预算/关键词 chips）、
  检索说明、条件放宽提示，以及每条结果的匹配原因与相关度。
- **管理后台**：Element Plus 表格 + 表单 + 分页，覆盖审核、举报、用户、地点、分类、任务日志与数据分析
  （数据分析以纯 CSS 条形图呈现，避免引入额外图表依赖）。

---

## 7. 验证情况（本机实测）

| 验证项          | 命令                                          | 结果                                                                                                                            |
| --------------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| 后端编译        | `backend> mvnw -B -DskipTests compile`        | ✅ BUILD SUCCESS（87 个源文件）                                                                                                  |
| 单元 + 集成测试 | `backend> mvnw test`                          | ✅ 10 个用例全部通过（分词/意图/排序/放宽/敏感词/距离 + 全链路接口）                                                             |
| PostgreSQL 迁移 | `spring-boot:run`（PostgreSQL 18，5433 实例） | ✅ Flyway 应用 V1 迁移，Hibernate validate 通过                                                                                  |
| 演示数据        | 启动日志                                      | ✅ 5 个校区（华农 2 正式 + 3 测试）、6 分类、90 地点、31 账号；1560 条模拟信息平均写入 3 个测试校区（520/520/520），正式校区为空 |
| 存量库升级      | 在已有数据库上重启                            | ✅ Flyway 仅新增应用 V2 迁移（校验和比对通过），Hibernate `validate` 通过，未重建数据                                            |
| 全新库初始化    | 空库启动（8081 验证）                         | ✅ Flyway 应用 V1+V2 → 校区/地点/账号/模拟信息全部就绪；华农两校区信息数为 0                                                     |
| 接口冒烟        | `scripts/smoke-test.ps1` 全量脚本             | ✅ 36 项检查全部通过（结果见 `_build/smoke-result.txt`）                                                                         |
| 前端构建        | `frontend> npm run build`                     | ✅ built in ~16s（20+ 页面按路由分包）                                                                                           |
| 前后端联调      | Vite dev server 5173 → 代理 8080              | ✅ `/api/schools` 正常返回中文数据                                                                                               |

冒烟测试覆盖：多租户切换、发布、地图视野、周边检索、AI 搜索意图与匹配原因、普通搜索、
协同验证与防刷（重复提交被拒）、评论、点赞、举报、AI 辅助建议、后台仪表盘/分类与校区分布、
高频证伪关注列表、过期治理任务、个人中心、检索引擎能力查询。

---

## 8. 已知边界与后续演进

1. **AI 能力**：当前为规则/统计实现（无大模型、无向量库）；后续接入 LLM 时，
   只需替换 `SearchService#aiSearch` 与 `AiPublishAdvisor`，接口契约不变。
2. **检索规模**：候选集在应用内打分（默认近 1200 条），适合校园级数据量；
   十万级以上建议启用 `db/optional_postgis.sql` 空间索引，并将打分迁移到 Elasticsearch / 向量检索。
3. **登录态**：令牌存于数据库（可切换 Redis）；密码使用盐 + SHA-256 迭代，
   生产环境建议替换为 BCrypt/Argon2 并接入学校统一身份认证。
4. **防刷**：当前基于「验证记录时间 + 唯一约束」实现冷却，生产环境建议叠加 Redis 计数限流。
5. **图片**：当前保存图片 URL，未接入对象存储。

---

## 9. 常见问题（FAQ）

### 9.1 地图上只有信息点，看不到底图？

原因：底图瓦片来自第三方服务，`tile.openstreetmap.org` 等国外源在国内网络下常被拦截或超时
（本机实测：OSM 官方 8s 超时、Carto/GeoQ 超时；OSM 德国镜像、高德、ESRI 正常）。

处理（已内置，无需改代码）：

1. **默认已使用可用源**：「OSM 标准地图（德国镜像）」，坐标系与系统数据一致；
2. 地图页右上角 **底图下拉框**可随时切换（含「高德地图（中文标注）」——国内网络最快，自动坐标纠偏）；
3. 若当前底图不可达，瓦片连续加载失败 3 次后会自动探测并回退到可用源，也可点「检测底图」手动触发；
4. 选择结果保存在浏览器 `localStorage.campus_basemap`，下次打开直接复用；
5. 若提示「未能连接到任何底图服务」，说明本机网络无法访问任何瓦片服务（如完全离线），
   此时信息点、搜索、发布等功能仍可正常使用；如需完全离线，可自行部署瓦片服务并加入 `frontend/src/utils/basemaps.js`。

> 生产环境建议：使用**天地图**（需申请 key）或自建/商业瓦片服务，并在 `basemaps.js` 中新增一条配置即可，
> 若其使用 GCJ-02 坐标系，只需把该项的 `crs` 设为 `'GCJ02'`，纠偏会自动生效。

### 9.2 后端启动失败：数据库连接被拒绝

检查三件事：① PostgreSQL 是否已启动（`docker compose up -d postgres` 或系统服务）；
② 数据库 `campus_info` 是否已创建；③ `DB_URL / DB_USERNAME / DB_PASSWORD` 是否正确
（默认 `jdbc:postgresql://localhost:5432/campus_info`、`postgres/postgres`）。

### 9.3 端口被占用（8080 / 5173 / 5432）

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

或修改 `backend/src/main/resources/application.yml` 的 `server.port`、`frontend/vite.config.js` 的 `server.port`。

### 9.4 用 PowerShell 直接运行 `scripts/smoke-test.ps1` 出现中文乱码或语法错误

Windows PowerShell 5.1 读取无 BOM 的 UTF-8 脚本会乱码，该脚本已保存为 **UTF-8 with BOM**；
若你编辑过它，请另存为「UTF-8 带 BOM」或改用 PowerShell 7（`pwsh`）。
