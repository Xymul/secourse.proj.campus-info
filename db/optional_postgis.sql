-- =====================================================================
-- 可选升级：启用 PostGIS 空间索引（在已执行 V1 迁移后手动执行）
--
-- 说明：
--   系统默认使用 double precision 存储经纬度 + 球面 Haversine 公式完成
--   空间检索，无需 PostGIS 即可运行；当数据量增大（十万级以上）时，
--   可按需启用 PostGIS 的 geography 类型与 GIST 索引获得更好的性能。
--
-- 执行方式（Docker）：
--   docker exec -i campus-postgres psql -U postgres -d campus_info < db/optional_postgis.sql
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- 1) 为帖子增加地理列，并与经纬度字段保持同步
ALTER TABLE post ADD COLUMN IF NOT EXISTS geog geography(Point, 4326);

UPDATE post
SET geog = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND geog IS NULL;

CREATE INDEX IF NOT EXISTS idx_post_geog ON post USING GIST (geog);

-- 2) 地点同样支持空间检索
ALTER TABLE campus_location ADD COLUMN IF NOT EXISTS geog geography(Point, 4326);

UPDATE campus_location
SET geog = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
WHERE geog IS NULL;

CREATE INDEX IF NOT EXISTS idx_location_geog ON campus_location USING GIST (geog);

-- 3) 自动同步触发器：写入经纬度时自动维护 geog 字段
CREATE OR REPLACE FUNCTION sync_post_geog() RETURNS trigger AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.geog := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_post_geog ON post;
CREATE TRIGGER trg_post_geog
    BEFORE INSERT OR UPDATE OF latitude, longitude ON post
    FOR EACH ROW EXECUTE FUNCTION sync_post_geog();

-- 4) 使用示例：查询某点 3 公里内的有效信息（可替换 PostSpatialRepository 中的 Haversine 语句）
--    SELECT p.post_id, ST_Distance(p.geog, ST_SetSRID(ST_MakePoint(120.2653, 31.4856), 4326)::geography) / 1000 AS distance_km
--    FROM post p
--    WHERE p.school_id = 1
--      AND p.status = 'PUBLISHED'
--      AND p.expire_at > now()
--      AND ST_DWithin(p.geog, ST_SetSRID(ST_MakePoint(120.2653, 31.4856), 4326)::geography, 3000)
--    ORDER BY distance_km ASC
--    LIMIT 50;
