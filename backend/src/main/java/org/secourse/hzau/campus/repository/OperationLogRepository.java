package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    @Query("""
            select l from OperationLog l
            where (:action is null or l.action = :action)
              and (:operatorId is null or l.operatorId = :operatorId)
              and (:keyword is null
                   or lower(l.detail) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(l.operatorName) like lower(concat('%', cast(:keyword as string), '%')))
            order by l.logId desc
            """)
    Page<OperationLog> search(@Param("action") String action,
                              @Param("operatorId") Long operatorId,
                              @Param("keyword") String keyword,
                              Pageable pageable);
}
