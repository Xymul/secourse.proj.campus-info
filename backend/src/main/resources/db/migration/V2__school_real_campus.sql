-- =====================================================================
-- 多校区正式化：区分「正式校区」与「测试数据校区」
--
--   1. school 表新增 is_test_data（是否为演示/测试数据）与 sort_order（排序，越小越靠前）；
--   2. 写入华中农业大学两个正式校区（坐标为 OSM/Photon 地理编码结果，WGS-84）：
--        · 狮子山主校区  30.473990, 114.348398（武汉市洪山区狮子山街）
--        · 襄阳校区      31.999918, 112.276105（襄阳市襄州区蔡州路）
--   3. 已存在的演示校区（江大/复旦/浙大）标记为测试数据并排在正式校区之后。
--
-- 说明：校园内的具体地点（图书馆、食堂等）由 DataSeeder 按模板为每个校区自动生成，
--       可在后台「地点核验 / 分类管理」中按实际情况修正。
-- =====================================================================

ALTER TABLE school ADD COLUMN IF NOT EXISTS is_test_data BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE school ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;

-- 正式校区：华中农业大学（置顶，is_test_data = false）
INSERT INTO school (code, name, city, center_latitude, center_longitude, default_zoom, status,
                    is_test_data, sort_order)
VALUES ('hzau-main', '华中农业大学（狮子山主校区）', '武汉', 30.473990, 114.348398, 16, 'ENABLED', FALSE, 1),
       ('hzau-xiangyang', '华中农业大学（襄阳校区）', '襄阳', 31.999918, 112.276105, 15, 'ENABLED', FALSE, 2)
ON CONFLICT (code) DO UPDATE
    SET name = EXCLUDED.name,
        city = EXCLUDED.city,
        center_latitude = EXCLUDED.center_latitude,
        center_longitude = EXCLUDED.center_longitude,
        default_zoom = EXCLUDED.default_zoom,
        is_test_data = FALSE,
        sort_order = EXCLUDED.sort_order;

-- 演示校区：标记为测试数据并排在正式校区之后（存在时才更新，全新库由 DataSeeder 建表时设置）
UPDATE school SET is_test_data = TRUE, sort_order = 10 WHERE code = 'jiangnan';
UPDATE school SET is_test_data = TRUE, sort_order = 11 WHERE code = 'fudan';
UPDATE school SET is_test_data = TRUE, sort_order = 12 WHERE code = 'zju';

CREATE INDEX IF NOT EXISTS idx_school_sort_order ON school (sort_order, school_id);
