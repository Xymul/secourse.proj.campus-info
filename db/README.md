# 数据库脚本说明

| 文件 | 是否必需 | 说明 |
| --- | --- | --- |
| `../backend/src/main/resources/db/migration/V1__init_schema.sql` | ✅ 必需 | 表结构（由 Flyway 在后端启动时自动执行，无需手工运行） |
| `optional_postgis.sql` | ⬜ 可选 | 启用 PostGIS：新增 `geography` 空间列、GIST 索引与自动同步触发器，可改用 `ST_DWithin` 加速周边查询 |
| `optional_mock_posts.sql` | ⬜ 可选 | 不启动后端时，用纯 SQL 生成 1500 余条模拟校园信息（后端启动时会自动生成等价数据） |

## 常用命令

```bash
# 1) 用 Docker 启动数据库
docker compose up -d postgres

# 2) 手工执行可选脚本（示例：Docker 容器名为 campus-postgres）
docker exec -i campus-postgres psql -U postgres -d campus_info < db/optional_postgis.sql

# 3) 查看数据概况
docker exec -it campus-postgres psql -U postgres -d campus_info -c \
  "select (select count(*) from school) as 学校, (select count(*) from campus_location) as 地点,
          (select count(*) from post) as 信息, (select count(*) from verification) as 验证,
          (select count(*) from comment) as 评论;"
```

> 提示：后端启动时 `DataSeeder` 会幂等初始化演示数据（学校、分类、地点、账号、1560 条信息），
> 只有在对应表为空时才写入，重复启动不会产生重复数据；如需关闭，设置 `CAMPUS_SEED_ENABLED=false`。
