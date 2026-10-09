package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 空间检索相关查询（LBS）。
 *
 * <p>采用球面 Haversine 公式在 SQL 中计算距离，无需 PostGIS 扩展即可运行；
 * 若已安装 PostGIS，可参考 {@code db/optional_postgis.sql} 启用 GIST 空间索引与 ST_DWithin 加速。</p>
 */
public interface PostSpatialRepository extends JpaRepository<Post, Long> {

    /** 周边 X 公里内的有效帖子，返回 [post_id, distance_km]，按距离升序 */
    @Query(value = """
            select p.post_id,
                   (6371 * acos(least(1.0, cos(radians(:lat)) * cos(radians(p.latitude))
                        * cos(radians(p.longitude) - radians(:lng))
                        + sin(radians(:lat)) * sin(radians(p.latitude))))) as distance
            from post p
            where p.school_id = :schoolId
              and p.status = 'PUBLISHED'
              and p.expire_at > :now
              and p.latitude is not null and p.longitude is not null
              and (6371 * acos(least(1.0, cos(radians(:lat)) * cos(radians(p.latitude))
                        * cos(radians(p.longitude) - radians(:lng))
                        + sin(radians(:lat)) * sin(radians(p.latitude))))) <= :radiusKm
            order by distance asc
            """, nativeQuery = true)
    List<Object[]> findNearbyPostIds(@Param("schoolId") Long schoolId,
                                    @Param("lat") double lat,
                                    @Param("lng") double lng,
                                    @Param("radiusKm") double radiusKm,
                                    @Param("now") LocalDateTime now,
                                    Pageable pageable);

    /** 地图视野加载：仅返回当前视窗范围内的信息节点，避免一次性加载全量数据 */
    @Query(value = """
            select p.post_id
            from post p
            where p.school_id = :schoolId
              and p.status = 'PUBLISHED'
              and p.expire_at > :now
              and p.latitude between :minLat and :maxLat
              and p.longitude between :minLng and :maxLng
            order by p.created_at desc
            """, nativeQuery = true)
    List<Object[]> findIdsInBounds(@Param("schoolId") Long schoolId,
                                  @Param("minLat") double minLat,
                                  @Param("maxLat") double maxLat,
                                  @Param("minLng") double minLng,
                                  @Param("maxLng") double maxLng,
                                  @Param("now") LocalDateTime now,
                                  Pageable pageable);
}
