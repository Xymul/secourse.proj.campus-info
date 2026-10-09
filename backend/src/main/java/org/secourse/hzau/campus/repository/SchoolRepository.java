package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.School;
import org.secourse.hzau.campus.entity.enums.CommonStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {

    Optional<School> findByCode(String code);

    /** 按排序值升序返回可用校区（正式校区置顶） */
    List<School> findAllByStatusOrderBySortOrderAscSchoolIdAsc(CommonStatus status);

    /** 全部校区（含禁用），同样按排序值升序 */
    List<School> findAllByOrderBySortOrderAscSchoolIdAsc();

    List<School> findAllByIsTestDataTrue();

    boolean existsByCode(String code);
}
