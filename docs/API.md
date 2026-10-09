# 此刻校园 · REST 接口文档

- 基地址：`http://localhost:8080/api`
- 统一响应结构：

```json
{ "code": 0, "message": "success", "data": { } }
```

`code = 0` 表示成功；`400` 参数/业务校验失败；`401` 未登录；`403` 无权限；`404` 资源不存在；`500` 服务端异常。
未登录时拦截器直接返回 HTTP 401/403 且响应体结构一致。

- 鉴权：`Authorization: Bearer <token>`（登录接口获取）。
- 分页参数：`page`（从 1 开始）、`size`。
- 时间格式：ISO-8601（如 `2026-09-18T16:44:53`）。

---

## 1. 账号与鉴权

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/auth/login` | 公开 | 登录，返回令牌与用户信息 |
| POST | `/auth/register` | 公开 | 注册并自动登录 |
| POST | `/auth/logout` | 需登录 | 注销当前令牌 |
| GET | `/auth/me` | 需登录 | 当前用户信息 |

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"student1","password":"123456"}'
```

---

## 2. 基础数据（公开）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/schools` | 校区列表（多租户切换）：按 `sortOrder` 升序，正式校区在前；含 `isTestData` 标记 |
| GET | `/categories?includeDisabled=false` | 分类列表（含信息数量） |
| GET | `/locations?schoolId=&keyword=&status=&lat=&lng=` | 地点列表（带坐标时按距离升序） |
| GET | `/schools/{schoolId}/locations` | 指定学校地点 |
| GET | `/meta/search-engine` | 检索引擎能力说明 |
| GET | `/meta/hot-keywords?schoolId=&limit=` | 搜索推荐词（近期信息高频标签） |

`/schools` 响应示例（节选）：

```json
{
  "code": 0,
  "data": [
    { "schoolId": 4, "code": "hzau-main", "name": "华中农业大学（狮子山主校区）", "city": "武汉",
      "centerLatitude": 30.47399, "centerLongitude": 114.348398, "defaultZoom": 16,
      "status": "ENABLED", "isTestData": false, "sortOrder": 1 },
    { "schoolId": 5, "code": "hzau-xiangyang", "name": "华中农业大学（襄阳校区）", "city": "襄阳",
      "centerLatitude": 31.999918, "centerLongitude": 112.276105, "defaultZoom": 15,
      "status": "ENABLED", "isTestData": false, "sortOrder": 2 },
    { "schoolId": 1, "code": "jiangnan", "name": "江南大学（蠡湖校区）",
      "isTestData": true, "sortOrder": 10 }
  ]
}
```

> `isTestData = true` 表示演示/测试数据校区（模拟信息只写入这些校区），前端会显示「测试数据」标记；
> 正式校区（华农）初始没有信息，由真实用户发布。

---

## 3. 信息节点（F1 / F3）

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/posts` | 公开 | 列表：`schoolId/categoryId/locationId/keyword/status/authorId/activeOnly/page/size/sort(latest\|hot\|expire\|trust)` |
| GET | `/posts/map` | 公开 | 地图视野加载：`schoolId + minLat/maxLat/minLng/maxLng + categoryId + limit`，同地点自动聚合 |
| GET | `/posts/nearby` | 公开 | 周边检索：`schoolId + lat + lng + radiusKm + categoryId + limit`（Haversine 距离，返回 `distanceKm`） |
| GET | `/posts/hot` | 公开 | 热门信息：`schoolId + categoryId + limit` |
| GET | `/posts/cluster?locationId=` | 公开 | 同地点信息聚合 |
| GET | `/posts/mine` | 需登录 | 我发布的信息 |
| GET | `/posts/{postId}` | 公开 | 详情（未登录时联系方式脱敏；浏览量 +1） |
| POST | `/posts` | 需登录 | 发布信息 |
| PUT | `/posts/{postId}` | 需登录（作者/管理员） | 编辑信息 |
| POST | `/posts/{postId}/resolve` | 需登录（作者/管理员） | 标记「已解决」 |
| DELETE | `/posts/{postId}?reason=` | 需登录（作者/管理员） | 下架信息 |

发布请求体：

```json
{
  "schoolId": 1, "categoryId": 4, "locationId": 12,
  "title": "在图书馆捡到校园卡一张",
  "content": "今天下午在图书馆三楼自习区捡到一张校园卡，请失主尽快联系我",
  "latitude": 31.4874, "longitude": 120.265,
  "contact": "13800001111", "tags": ["失物招领", "图书馆"],
  "validDays": 7, "imageUrl": null, "aiAssisted": true
}
```

> 有效期：`validDays` 与 `expireAt` 二选一；都不传时按分类默认有效期计算，且必须晚于当前时间。
> 若标题/正文命中高危敏感词，发布后状态直接为 `PENDING`（待审核）。

---

## 4. 信息搜索（F2）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/search/keyword?q=&schoolId=&categoryId=&locationId=&page=&size=` | 普通关键词搜索（标题/正文/标签 LIKE + 热度排序） |
| GET | `/search/ai?q=&schoolId=&lat=&lng=&radiusKm=&categoryId=&limit=` | AI 智能搜索（意图解析 + 相关性排序 + 匹配原因 + 条件放宽） |

AI 搜索响应（节选）：

```json
{
  "code": 0,
  "data": {
    "query": "附近1公里的二手台灯 预算300元",
    "intent": {
      "categoryCode": "SECONDHAND", "categoryName": "二手交易",
      "locationName": null, "timeLabel": null,
      "nearby": true, "radiusKm": 1.0, "maxPrice": 300.0,
      "keywords": ["二手", "台灯", "预算"],
      "understood": true, "degradeReason": null
    },
    "list": [
      { "postId": 812, "title": "出闲置台灯，120元可小刀",
        "relevance": 100, "distanceKm": 0.42,
        "matchReasons": ["标题包含「台灯」", "分类匹配：二手交易", "距您约 420 米", "价格符合预算（约 120 元）"] }
    ],
    "appliedConditions": ["关键词：二手、台灯", "分类：二手交易", "距离：1.0 公里内"],
    "relaxedConditions": [],
    "fallback": false,
    "explanation": "已理解您的需求：分类=二手交易；距离=1.0 公里内；预算≤300 元；关键词=二手、台灯。",
    "mode": "ai"
  }
}
```

---

## 5. AI 辅助发布（F3）

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/ai/suggest` | 需登录 | 根据标题/正文建议分类、标签、有效期与风险提示 |

```json
// 请求
{ "title": "急！在操场捡到一副蓝牙耳机", "content": "今天傍晚在田径场捡到一副蓝牙耳机，请失主联系我", "schoolId": 1 }

// 响应 data
{
  "categoryCode": "LOST_FOUND", "categoryName": "失物招领", "categoryConfidence": 0.85,
  "categoryReason": "命中意图词：捡到、失物招领",
  "tags": ["失物招领", "拾金不昧", "急寻", "操场", "捡到"],
  "suggestedValidDays": 3,
  "validDaysReason": "内容包含「急/今天/限时」等时效性词汇，建议 3 天内有效",
  "riskWords": [], "riskLevel": "LOW", "contactDetected": false,
  "summary": "已根据标题与正文生成建议：分类「失物招领」，有效期 3 天，标签 失物招领、拾金不昧…；全部建议均可修改。",
  "tips": ["分类建议：失物招领（可手动修改）", "内容包含「急/今天/限时」等时效性词汇，建议 3 天内有效"]
}
```

---

## 6. 互动与协同验证（F4）

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/posts/{postId}/comments` | 公开 | 评论列表 |
| POST | `/posts/{postId}/comments` | 需登录 | 发表评论 `{"content":"..."}` |
| POST | `/posts/{postId}/like` | 需登录 | 点赞 |
| DELETE | `/posts/{postId}/like` | 需登录 | 取消点赞 |
| GET | `/posts/{postId}/verifications` | 公开 | 验证汇总（含我的立场） |
| POST | `/posts/{postId}/verify` | 需登录 | 提交证实/证伪/存疑 `{"result":"TRUE","remark":"..."}` |
| POST | `/posts/{postId}/claims` | 需登录 | 提交失物认领申请 |
| GET | `/posts/{postId}/claims` | 需登录（发布者/管理员） | 认领申请列表 |
| POST | `/claims/{claimId}/handle` | 需登录（发布者/管理员） | 处理认领 `{"action":"APPROVED"\|"REJECTED","remark":"..."}` |
| GET | `/me/interactions` | 需登录 | 我的发布 / 点赞 / 验证 / 认领 |

验证汇总响应：

```json
{
  "trueCount": 5, "falseCount": 1, "unknownCount": 0, "total": 6,
  "trustRatio": 0.83, "trustPercent": 83, "myResult": "TRUE",
  "level": "可信度高", "warned": false
}
```

> 规则：同一用户对同一信息仅保留一条验证记录（重复提交视为修改立场，由 `verification(post_id,user_id)` 唯一约束保证）；
> 20 秒内重复提交返回「操作过于频繁」；证伪数达到阈值（默认 3）时写入后台预警并进入关注列表。

---

## 7. 举报（F5）

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/posts/{postId}/reports` | 需登录 | 举报 `{"reason":"信息已过期","detail":"..."}` |

> 同一用户对同一信息只能举报一次；举报数达到阈值（默认 2）时帖子自动转入 `PENDING` 待审核。

---

## 8. 管理后台（F6，均需 ADMIN 角色）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/dashboard?schoolId=` | 仪表盘统计（总量 / 状态分布 / 分类与校区分布 / 7 天趋势 / 最近任务与日志） |
| GET | `/admin/analytics?schoolId=` | 数据分析（分类、校区、状态、地点 TOP、互动汇总、热门标签、14 天趋势） |
| GET | `/admin/posts` | 内容列表：`schoolId/status/keyword/page/size` |
| GET | `/admin/posts/stats?schoolId=` | 各状态数量统计 |
| GET | `/admin/posts/flagged?limit=` | 高频证伪 / 多次举报的关注列表 |
| POST | `/admin/posts/{postId}/audit` | 审核：`{"action":"APPROVE"\|"REJECT"\|"REMOVE"\|"RESTORE"\|"RESOLVE","remark":"..."}` |
| GET | `/admin/users` | 用户列表：`schoolId/status/role/keyword/page/size` |
| POST | `/admin/users/{userId}/status` | 启用/禁用：`{"status":"DISABLED","remark":"..."}`（禁用同时吊销令牌） |
| GET | `/admin/reports` | 举报列表：`status/keyword/page/size` |
| POST | `/admin/reports/{reportId}/handle` | 处理举报：`{"action":"HANDLE"\|"REJECT","remark":"..."}`（成立则下架信息） |
| GET | `/admin/categories` | 分类列表（含禁用） |
| POST / PUT | `/admin/categories[/{categoryId}]` | 新增 / 编辑分类（名称、描述、默认有效期、排序、启用状态） |
| GET | `/admin/locations` | 地点列表：`schoolId/status/keyword` |
| POST / PUT | `/admin/locations[/{locationId}]` | 新增 / 编辑地点 |
| POST | `/admin/locations/{locationId}/verify?approved=&remark=` | 地点核验 |
| DELETE | `/admin/locations/{locationId}` | 删除地点（存在关联信息时拒绝） |
| GET | `/admin/tasks?page=&size=` | 定时任务执行记录 |
| POST | `/admin/tasks/expire` | 手动触发过期治理（演示 / 运维） |
| GET | `/admin/logs?action=&operatorId=&keyword=&page=&size=` | 操作日志 |

---

## 9. 错误码对照

| code | 含义 | 典型场景 |
| --- | --- | --- |
| 0 | 成功 | — |
| 400 | 请求或业务校验失败 | 参数缺失、有效期早于当前时间、重复点赞、验证过于频繁 |
| 401 | 未登录或令牌过期 | 令牌缺失 / 过期 / 账号被禁用 |
| 403 | 无权限 | 普通用户访问 `/admin/**`、非作者编辑他人信息 |
| 404 | 资源不存在 | 信息 / 地点 / 分类 / 举报记录不存在 |
| 500 | 服务端异常 | 数据库异常等（日志中带完整堆栈） |
