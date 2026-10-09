-- =====================================================================
-- 可选：使用纯 SQL 生成 1500 余条模拟校园信息
--
-- 说明：
--   后端启动时会由 MockPostGenerator 自动生成模拟数据（默认 1560 条），
--   本脚本提供「不想启动服务、直接用 SQL 灌数据」的等价方案，
--   需要先完成 V1 迁移并在库中存在学校 / 分类 / 地点 / 用户数据。
--
-- 执行：psql -h 127.0.0.1 -p 5432 -U postgres -d campus_info -f db/optional_mock_posts.sql
-- =====================================================================

-- 1) 三个校区
INSERT INTO school (code, name, city, center_latitude, center_longitude, default_zoom, status)
VALUES ('jiangnan', '江南大学（蠡湖校区）', '无锡', 31.485600, 120.265300, 16, 'ENABLED'),
       ('fudan', '复旦大学（邯郸校区）', '上海', 31.298900, 121.499800, 16, 'ENABLED'),
       ('zju', '浙江大学（紫金港校区）', '杭州', 30.308400, 120.086300, 16, 'ENABLED')
ON CONFLICT (code) DO NOTHING;

-- 2) 分类
INSERT INTO category (code, name, description, color, base_valid_days, sort_order, status)
VALUES ('TALK', '分享吐槽', '校园生活体验、食堂测评、日常吐槽', '#F6A6A6', 7, 0, 'ENABLED'),
       ('TEAM', '组队交友', '自习、运动、出行等组队与交友', '#7FB3D5', 7, 1, 'ENABLED'),
       ('SECONDHAND', '二手交易', '闲置转让、求购与低价出清', '#F7B267', 14, 2, 'ENABLED'),
       ('LOST_FOUND', '失物招领', '丢失物品发布与招领认领', '#9DC88D', 30, 3, 'ENABLED'),
       ('ACTIVITY', '校园活动', '讲座、比赛、招新等校园活动', '#B39DDB', 7, 4, 'ENABLED'),
       ('OTHER', '其他', '咨询求助与其他校园信息', '#B0BEC5', 7, 5, 'ENABLED')
ON CONFLICT (code) DO NOTHING;

-- 3) 每个校区各生成 9 个地点（以学校中心点叠加偏移量）
INSERT INTO campus_location (school_id, name, area, latitude, longitude, description, status)
SELECT s.school_id,
       v.name,
       v.area,
       round((s.center_latitude + v.dy)::numeric, 6),
       round((s.center_longitude + v.dx)::numeric, 6),
       v.area || '内的常用信息点',
       'VERIFIED'
FROM school s
CROSS JOIN (VALUES ('图书馆', '教学区', 0.0015, -0.0008),
                   ('第一食堂', '生活区', 0.0008, 0.0012),
                   ('第二食堂', '生活区', -0.0006, 0.0015),
                   ('学生公寓A区', '生活区', -0.0018, -0.0012),
                   ('田径场', '运动区', 0.0025, 0.0018),
                   ('篮球场', '运动区', 0.0021, 0.0026),
                   ('教学楼A座', '教学区', 0.0006, -0.0018),
                   ('快递驿站', '生活区', -0.0012, 0.0022),
                   ('大学生活动中心', '活动区', 0.0012, 0.0006)) AS v(name, area, dx, dy)
WHERE NOT EXISTS (
    SELECT 1 FROM campus_location l WHERE l.school_id = s.school_id AND l.name = v.name
);

-- 4) 演示用户（密码哈希由后端 PasswordEncoder 生成，此处仅占位，请通过接口注册正式账号）
INSERT INTO app_user (school_id, username, nickname, password_hash, role, status, credit_score, contact)
SELECT s.school_id,
       'demo_' || s.code || '_' || g.i,
       '演示用户' || g.i,
       'placeholder$please_register_via_api',
       'USER',
       'ACTIVE',
       100,
       '1390000' || lpad(g.i::text, 4, '0')
FROM school s
CROSS JOIN generate_series(1, 10) AS g(i)
ON CONFLICT (username) DO NOTHING;

-- 5) 生成 1500 余条帖子：随机分类 / 地点 / 作者，并覆盖不同生命周期状态
INSERT INTO post (school_id, category_id, location_id, user_id, title, content, latitude, longitude,
                  tags, ai_assisted, expire_at, status, verify_true_count, verify_false_count,
                  view_count, like_count, hot_score, created_at, updated_at)
SELECT l.school_id,
       c.category_id,
       l.location_id,
       u.user_id,
       (ARRAY['在', '关于', '急寻', '转让', '组队', '分享】'])[1 + floor(random() * 6)] || l.name || '的' ||
       (ARRAY['校园卡', '蓝牙耳机', '山地自行车', '考研自习', '就业宣讲会', '食堂新窗口', '打印店'])[1 + floor(random() * 7)],
       '由 SQL 脚本生成的模拟信息：地点在' || l.name || '，分类为' || c.name || '，内容仅用于演示与压力测试。',
       round((l.latitude + (random() - 0.5) * 0.0006)::numeric, 6),
       round((l.longitude + (random() - 0.5) * 0.0006)::numeric, 6),
       c.name || ',' || l.name,
       random() < 0.4,
       (now() - (random() * interval '20 days') + (c.base_valid_days || ' days')::interval),
       CASE
           WHEN random() < 0.82 THEN 'PUBLISHED'
           WHEN random() < 0.90 THEN 'PENDING'
           WHEN random() < 0.95 THEN 'RESOLVED'
           ELSE 'REMOVED'
           END,
       floor(random() * 6)::int,
       floor(random() * 3)::int,
       floor(random() * 320)::int,
       floor(random() * 16)::int,
       random() * 3,
       now() - (random() * interval '18 days'),
       now() - (random() * interval '18 days')
FROM generate_series(1, 1560) AS g(i)
         JOIN campus_location l ON l.location_id = 1 + floor(random() * 9)::int * 3 -- 每个校区取一个地点
         JOIN category c ON c.category_id = 1 + floor(random() * 6)::int
         JOIN app_user u ON u.school_id = l.school_id
WHERE NOT EXISTS (SELECT 1 FROM post LIMIT 1);

-- 6) 修正已过期的帖子状态
UPDATE post SET status = 'EXPIRED' WHERE expire_at < now() AND status IN ('PUBLISHED', 'PENDING');

-- 7) 统计
SELECT '学校' AS 对象, count(*) AS 数量 FROM school
UNION ALL SELECT '分类', count(*) FROM category
UNION ALL SELECT '地点', count(*) FROM campus_location
UNION ALL SELECT '信息', count(*) FROM post;
