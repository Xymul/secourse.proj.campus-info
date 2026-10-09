package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.CampusLocation;
import org.secourse.hzau.campus.entity.enums.LocationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CampusLocationRepository extends JpaRepository<CampusLocation, Long> {

    List<CampusLocation> findAllBySchool_SchoolIdAndStatusOrderByNameAsc(Long schoolId, LocationStatus status);

    List<CampusLocation> findAllBySchool_SchoolIdOrderByNameAsc(Long schoolId);

    long countBySchool_SchoolId(Long schoolId);

    @Query("""
            select l from CampusLocation l join fetch l.school s
            where (:schoolId is null or s.schoolId = :schoolId)
              and (:status is null or l.status = :status)
              and (:keyword is null
                   or lower(l.name) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(l.area) like lower(concat('%', cast(:keyword as string), '%')))
            order by l.locationId desc
            """)
    Page<CampusLocation> search(@Param("schoolId") Long schoolId,
                               @Param("status") LocationStatus status,
                               @Param("keyword") String keyword,
                               Pageable pageable);

    boolean existsBySchool_SchoolIdAndName(Long schoolId, String name);

    /** 周边地点：按距离排序的简易 Haversine 计算（与帖子周边检索保持一致） */
    @Query(value = """
            select l.location_id,
                   (6371 * acos(least(1, cos(radians(:lat)) * cos(radians(l.latitude))
                        * cos(radians(l.longitude) - radians(:lng))
                        + sin(radians(:lat)) * sin(radians(l.latitude))))) as distance
            from campus_location l
            where l.school_id = :schoolId
            order by distance asc
            limit :limit
            """, nativeQuery = true)
    List<Object[]> findNearestLocationIds(@Param("schoolId") Long schoolId,
                                         @Param("lat") double lat,
                                         @Param("lng") double lng,
                                         @Param("limit") int limit);
}
