# 构建说明（BUILD）

> 本文档说明「此刻校园」项目的构建方式：环境要求、构建命令、构建配置要点、产物位置、验证方式与常见问题。
> 使用说明（功能、账号、接口、FAQ）见 [`README.md`](README.md)；接口清单见 [`docs/API.md`](docs/API.md)；
> 在 Debian 13 等 Linux 服务器上的依赖安装、运行与部署见 [`REPORT.md`](REPORT.md)。

---

## 1. 项目构成与构建产物

| 模块       | 目录                                               | 技术栈                                   | 构建产物                                                              |
| ---------- | -------------------------------------------------- | ---------------------------------------- | --------------------------------------------------------------------- |
| 后端服务   | `backend/`                                         | Spring Boot 3.5 + Java 17（Maven）       | `backend/target/campus-info-platform-backend-1.0.0.jar`（可执行 jar） |
| 前端应用   | `frontend/`                                        | Vue 3 + Vite + OpenLayers + Element Plus | `frontend/dist/`（静态资源，按路由分包）                              |
| 数据库     | `db/` + `backend/src/main/resources/db/migration/` | PostgreSQL + Flyway                      | 表结构由 Flyway 在启动时自动建立                                      |
| 自动化脚本 | `scripts/`                                         | Windows 批处理 / PowerShell              | 启动、停止、初始化与冒烟测试脚本                                      |

后端与前端彼此独立构建；后端启动时自动执行数据库迁移与（可选的）演示数据初始化，因此**无需手工建表**。

---

## 2. 环境要求与实测版本

### 2.1 必需环境

| 组件       | 最低要求 | 本项目实测版本                                 | 说明                                                              |
| ---------- | -------- | ---------------------------------------------- | ----------------------------------------------------------------- |
| JDK        | 17+      | **Oracle JDK 23**（`javac 23`）                | 编译目标固定为 Java 17（`--release 17`），JDK 17~24 均可构建      |
| Maven      | 3.8+     | **3.9.16**（由 `backend/mvnw` 包装器自动下载） | 无需本机预装 Maven                                                |
| Node.js    | 18+      | **v20.19.4**                                   | 仅前端构建需要                                                    |
| npm        | 9+       | **10.8.2**                                     | 随 Node 提供                                                      |
| PostgreSQL | 14+      | **18.6**                                       | 主数据库；或用 Docker `postgis/postgis:16-3.4`                    |
| 操作系统   | —        | Windows（PowerShell 5.1）                      | 脚本为 `.cmd` / `.ps1`，Linux/macOS 使用等价命令（见 §3.2、§4.1） |

### 2.2 后端依赖（构建时实际解析到的版本）

| 依赖                           | 版本                                       | 用途                                             |
| ------------------------------ | ------------------------------------------ | ------------------------------------------------ |
| Spring Boot                    | 3.5.16                                     | 父 POM / 依赖管理                                |
| spring-boot-starter-web        | 3.5.16                                     | REST 接口、内嵌 Tomcat                           |
| spring-boot-starter-data-jpa   | 3.5.16                                     | JPA / 事务                                       |
| spring-boot-starter-validation | 3.5.16                                     | 请求参数校验                                     |
| Hibernate ORM                  | 6.6.53.Final                               | ORM 实现（含 `@Version` 乐观锁、`@EntityGraph`） |
| Flyway                         | 11.7.2 + flyway-database-postgresql 11.7.2 | 数据库版本管理                                   |
| PostgreSQL JDBC                | 42.7.11                                    | 数据库驱动                                       |
| H2                             | 2.3.232                                    | `h2` profile 与集成测试的内存库                  |
| Lombok                         | 1.18.46                                    | 实体/DTO 样板代码                                |
| spring-boot-starter-test       | 3.5.16                                     | JUnit 5 + AssertJ + MockMvc                      |

### 2.3 前端依赖（`package-lock.json` 实测版本）

| 依赖               | 版本    | 用途                               |
| ------------------ | ------- | ---------------------------------- |
| vue                | 3.5.43  | 前端框架                           |
| vue-router         | 4.6.4   | 路由（Hash 模式，便于静态部署）    |
| vite               | 5.4.21  | 构建与开发服务器                   |
| @vitejs/plugin-vue | 5.2.4   | SFC 编译                           |
| ol（OpenLayers）   | 10.10.0 | 校园地图、瓦片底图、矢量图层与弹窗 |
| element-plus       | 2.14.5  | 后台管理界面与表单组件             |
| axios              | 1.20.0  | HTTP 客户端                        |

---

## 3. 后端构建

### 3.1 目录与关键配置

```
backend/
├─ pom.xml                         # 依赖与编译插件配置（见 §3.5）
├─ mvnw / mvnw.cmd / .mvn/         # Maven 包装器（首次构建自动下载 Maven 3.9.16）
└─ src/
   ├─ main/java/org/secourse/hzau/campus/   # 87 个 Java 源文件
   ├─ main/resources/application.yml        # 运行配置（profile：postgres / h2）
   └─ main/resources/db/migration/          # Flyway 迁移：V1 表结构、V2 正式/测试校区
```

### 3.2 常用构建命令

```bat
:: Windows
cd backend
mvnw.cmd -B -DskipTests compile     :: 仅编译
mvnw.cmd -B test                    :: 单元测试 + 集成测试（H2 内存库）
mvnw.cmd -B -DskipTests package     :: 打包可执行 jar
mvnw.cmd -B spring-boot:run         :: 直接运行（开发用）
```

```bash
# Linux / macOS
cd backend
./mvnw -B -DskipTests package
java -jar target/campus-info-platform-backend-1.0.0.jar
```

### 3.3 运行产物与环境变量

```bash
java -jar backend/target/campus-info-platform-backend-1.0.0.jar
```

| 环境变量                      | 默认值                                         | 说明                                               |
| ----------------------------- | ---------------------------------------------- | -------------------------------------------------- |
| `DB_URL`                      | `jdbc:postgresql://localhost:5432/campus_info` | 数据库连接串                                       |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres`                        | 数据库账号                                         |
| `CAMPUS_SEED_ENABLED`         | `true`                                         | 是否初始化演示数据（校区/分类/地点/账号/模拟信息） |
| `CAMPUS_MOCK_POSTS`           | `1560`                                         | 模拟信息条数（仅写入测试数据校区）                 |
| `SPRING_PROFILES_ACTIVE`      | `postgres`                                     | 切换为 `h2` 可用内存库零依赖体验                   |
| `SERVER_PORT`                 | `8080`                                         | 服务端口                                           |

### 3.4 零依赖快速构建体验（H2）

```bat
cd backend
set SPRING_PROFILES_ACTIVE=h2
mvnw.cmd -B spring-boot:run
```

`h2` profile 关闭 Flyway、改由 JPA 依据实体自动建表（`ddl-auto=create-drop`），
因此**不做 Flyway/PostgreSQL 迁移验证**，仅用于快速试跑；正式构建请使用 `postgres` profile。

### 3.5 关键构建配置说明（`backend/pom.xml`）

| 配置                                                                               | 作用                                                                             |
| ---------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `java.version=17` + `project.build.sourceEncoding=UTF-8`                           | 编译目标 Java 17，源码按 UTF-8 读取（中文注释/字符串必需）                       |
| `maven-compiler-plugin` → `<proc>full</proc>` + `annotationProcessorPaths(lombok)` | **JDK 23 起 javac 不再隐式启用注解处理**，不配置会报「找不到符号 setXxx/getXxx」 |
| `spring-boot-maven-plugin`                                                         | 打包可执行 fat jar；排除 Lombok                                                  |
| `maven-surefire-plugin` → `-Dfile.encoding=UTF-8`                                  | 测试输出与断言中的中文不乱码                                                     |
| `spring.jpa.hibernate.ddl-auto=validate`（postgres profile）                       | 启动时校验「实体 = Flyway 建出的表结构」，防止二者漂移                           |
| `spring.flyway.locations=classpath:db/migration`                                   | 迁移脚本位置；启动时自动执行 V1、V2…                                             |

> 提示：若把 `lombok` 依赖的 `<scope>` 改成 `provided` 或升级到更新的 JDK，请同步确认
> `annotationProcessorPaths` 中 Lombok 版本与 JDK 兼容（本项目实测 1.18.46 + JDK 23 正常）。

---

## 4. 前端构建

### 4.1 命令

```bat
cd frontend
npm install          :: 首次安装依赖（或 npm ci 按 lock 文件精确安装）
npm run dev          :: 开发服务器 http://127.0.0.1:5173，/api 代理到 8080
npm run build        :: 生产构建，输出到 frontend/dist
npm run preview      :: 本地预览构建产物（端口 4173）
```

### 4.2 构建配置要点（`frontend/vite.config.js`）

| 配置                                                  | 说明                                                                      |
| ----------------------------------------------------- | ------------------------------------------------------------------------- |
| `server.proxy['/api'] → http://127.0.0.1:8080`        | 开发环境免跨域；生产由 Nginx 反向代理                                     |
| `build.outDir = dist`、`chunkSizeWarningLimit = 1600` | 产物目录与告警阈值（Element Plus 体积较大）                               |
| 路由懒加载（`router/index.js` 中的 `import()`）       | 每个页面独立分包，首屏只加载首页与公共库                                  |
| `base`（默认 `/`）                                    | 若部署在子路径（如 `/campus/`），需设置 `base: '/campus/'` 并同步调整代理 |

> 后端已开启 CORS（`WebConfig`），也可直接用完整后端地址构建：
> 设置环境变量 `VITE_API_BASE=http://<后端地址>:8080/api` 后执行 `npm run build`。

### 4.3 部署（Nginx 示例）

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;          # 对应 frontend/dist 内容
    index index.html;

    location /api/ {                     # 与后端接口对应
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {                         # Hash 路由，无需 history 回退配置
        try_files $uri $uri/ /index.html;
    }
}
```

### 4.4 底图（地图瓦片）依赖网络

地图底图来自第三方瓦片服务，**运行时需要能连通所选瓦片源**；构建本身不依赖网络。
默认使用「OSM 标准地图（德国镜像）」，并内置多底图切换与自动探测回退（详见 `README.md` §9.1）。
若部署在完全离线的内网，可自建瓦片服务并在 `frontend/src/utils/basemaps.js` 中新增配置。

---

## 5. 数据库构建

| 步骤              | 方式                                                                                       | 说明                                                      |
| ----------------- | ------------------------------------------------------------------------------------------ | --------------------------------------------------------- |
| 建库              | `docker compose up -d postgres` 或本机 PostgreSQL 手动创建 `campus_info`                   | 只需空库                                                  |
| 建表              | 后端启动时 Flyway 自动执行 `V1__init_schema.sql`、`V2__school_real_campus.sql`             | Hibernate 仅做校验，**不要**手工建表                      |
| 演示数据          | 后端启动时 `DataSeeder` 幂等初始化                                                         | 校区、分类、地点、账号、模拟信息（仅写入测试数据校区）    |
| 可选：PostGIS     | `docker exec -i campus-postgres psql -U postgres -d campus_info < db/optional_postgis.sql` | 启用 geography + GIST 空间索引（默认 Haversine 无需此步） |
| 可选：纯 SQL 造数 | `psql ... -f db/optional_mock_posts.sql`                                                   | 不启动后端时生成 1500+ 模拟信息                           |

```bash
# 校验迁移与数据是否就绪
docker exec -it campus-postgres psql -U postgres -d campus_info -c \
  "select code, name, is_test_data, sort_order from school order by sort_order;"
```

---

## 6. 测试与验证

### 6.1 后端测试（10 个用例）

```bat
cd backend && mvnw.cmd -B test
```

| 测试类                          | 用例数 | 覆盖内容                                                                                                                                                                            |
| ------------------------------- | ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `search.SimpleSearchEngineTest` | 9      | 中文分词、停用词过滤、意图解析（分类/地点/时间/距离/预算）、相关性排序与匹配原因、条件放宽、AI 辅助发布建议、敏感词检测、Haversine 距离                                             |
| `CampusApiIntegrationTest`      | 1      | 端到端：校区结构（华农置顶 + 3 个测试数据校区）→ 登录 → AI 建议 → 发布 → 地图/周边/搜索（含华农正式校区）→ 协同验证与防刷 → 评论点赞 → 举报 → 后台审核/举报处理/过期治理 → 权限校验 |

> 集成测试使用 `test` profile（H2 内存库 + `ddl-auto=create-drop` + 120 条模拟信息），
> 不依赖 PostgreSQL，可在无数据库环境下执行。

### 6.2 接口冒烟测试（对运行中的服务）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
# 结果写入 _build\smoke-result.txt
```

### 6.3 构建与算法校验辅助脚本（`_build/`）

| 脚本                                                  | 用途                                                                   |
| ----------------------------------------------------- | ---------------------------------------------------------------------- |
| `check-geo.mjs`                                       | 校验 WGS-84 ↔ GCJ-02 纠偏与瓦片编号换算（`node _build/check-geo.mjs`） |
| `verify-hzau.mjs`                                     | 输出华农两校区在各底图下的核验瓦片 URL                                 |
| `geocode.ps1`                                         | 地理编码正反向核对校区坐标（需外网）                                   |
| `do-compile.cmd` / `do-test.cmd` / `do-npm-build.cmd` | 后台编译、测试、构建并将日志落盘，便于轮询                             |

---

## 7. 一键脚本清单（`scripts/`）

| 脚本                 | 作用                                                                                |
| -------------------- | ----------------------------------------------------------------------------------- |
| `init-local-db.cmd`  | 初始化并启动仓库自带的 PostgreSQL 实例（端口 5433，trust 认证，不干扰系统原有服务） |
| `stop-local-db.cmd`  | 停止该实例                                                                          |
| `start-backend.cmd`  | 按 `DB_URL/DB_USERNAME/DB_PASSWORD` 启动后端（默认 5432）                           |
| `start-frontend.cmd` | 安装依赖（首次）并启动前端开发服务器                                                |
| `smoke-test.ps1`     | 全链路接口冒烟测试（UTF-8 with BOM，兼容 Windows PowerShell 5.1）                   |

---

## 8. 构建验证记录（本机实测）

| 环节       | 命令                               | 结果                                                                                      |
| ---------- | ---------------------------------- | ----------------------------------------------------------------------------------------- |
| 后端编译   | `mvnw.cmd -B -DskipTests compile`  | ✅ BUILD SUCCESS（87 个源文件，约 33s）                                                    |
| 后端测试   | `mvnw.cmd -B test`                 | ✅ Tests run: 10, Failures: 0, Errors: 0（约 1m11s）                                       |
| 后端运行   | `mvnw.cmd -B spring-boot:run`      | ✅ Flyway 应用 V1/V2 迁移；Hibernate `validate` 通过；Tomcat 8080                          |
| 演示数据   | 启动日志                           | ✅ 5 个校区（华农 2 个正式 + 3 个测试）、6 分类、地点、账号、1560 条模拟信息（仅测试校区） |
| 前端依赖   | `npm install`                      | ✅ 104 个包，约 28s                                                                        |
| 前端构建   | `npm run build`                    | ✅ built in 约 7s（20+ 页面按路由分包）                                                    |
| 前后端联调 | Vite 5173 → 8080 → PostgreSQL 5433 | ✅ `/api/schools` 正常返回（含新字段 `isTestData`、`sortOrder`）                           |

---

## 9. 常见构建问题排查

| 现象                                                | 原因                                   | 处理                                                                                                                |
| --------------------------------------------------- | -------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| 编译报「找不到符号 `setTitle(...)` / `getXxx()`」   | JDK 23+ 未显式启用 Lombok 注解处理     | 确认 `pom.xml` 中 `maven-compiler-plugin` 的 `<proc>full</proc>` 与 `annotationProcessorPaths` 存在                 |
| 控制台中文乱码（源码/日志）                         | 源码编码与平台默认不一致               | 已用 `project.build.sourceEncoding=UTF-8` 与 surefire `-Dfile.encoding=UTF-8` 处理；新增文件请同样保存为 UTF-8      |
| PowerShell 运行 `.ps1` 报语法错误或乱码             | PS 5.1 按 GBK 读取无 BOM 的 UTF-8 脚本 | 脚本另存为「UTF-8 带 BOM」，或改用 PowerShell 7（`pwsh`）                                                           |
| `mvn` 不是内部或外部命令                            | 未安装 Maven                           | 使用仓库自带包装器 `mvnw.cmd`（首次会自动下载 Maven 3.9.16）                                                        |
| 启动报 `FlywayValidateException: checksum mismatch` | 迁移脚本被修改过                       | 已执行的迁移文件不可修改，新增变更请新建 `V3__xxx.sql`；开发库可清库重建                                            |
| 启动报 `Schema-validation: missing column xxx`      | 实体新增字段但缺迁移脚本               | 新增迁移脚本补齐列（本项目 V2 即为此场景示例）                                                                      |
| 端口被占用（8080/5173/5432）                        | 上一实例未退出                         | `Get-NetTCPConnection -LocalPort 8080 -State Listen \| ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }` |
| `npm install` 卡住或失败                            | 网络/代理限制                          | `npm config set registry https://registry.npmmirror.com`，或设置 `HTTPS_PROXY`                                      |
| 地图只有信息点没有底图                              | 瓦片源不可达（部分国外源被拦截）       | 见 `README.md` §9.1：切换/自动回退底图，或自建瓦片服务                                                              |

---

## 10. 依赖与配置升级指引

| 想改的内容              | 修改位置                                                                                                                      |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Spring Boot / Java 版本 | `backend/pom.xml`：`parent.version`、`java.version`（改 Java 版本后需重跑测试）                                               |
| 前端依赖版本            | `frontend/package.json` 后执行 `npm install`（提交 `package-lock.json` 锁定）                                                 |
| 数据库连接              | 环境变量 `DB_URL/DB_USERNAME/DB_PASSWORD`，或 `application.yml` 的 `postgres` profile                                         |
| 演示数据规模与开关      | `CAMPUS_MOCK_POSTS`、`CAMPUS_SEED_ENABLED`、`campus.seed.*`                                                                   |
| 新增/调整校区           | `backend/src/main/java/org/secourse/hzau/campus/seed/DataSeeder.java` 的 `SCHOOLS`（坐标、排序、`testData` 标记），重启即生效 |
| 新增信息分类            | 后台「分类管理」页面，或 `DataSeeder.CATEGORIES`                                                                              |
| 新增底图源              | `frontend/src/utils/basemaps.js` 的 `BASEMAPS`（GCJ-02 源把 `crs` 设为 `'GCJ02'` 即自动纠偏）                                 |
| 新增业务表/字段         | 新增 `backend/src/main/resources/db/migration/V3__xxx.sql` 并同步实体类                                                       |
