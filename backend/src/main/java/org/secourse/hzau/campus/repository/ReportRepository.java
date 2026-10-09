package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.Report;
import org.secourse.hzau.campus.entity.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    long countByStatus(ReportStatus status);

    long countByPost_PostId(Long postId);

    boolean existsByPost_PostIdAndReporter_UserId(Long postId, Long userId);

    @EntityGraph(attributePaths = {"post", "reporter", "post.category", "post.author"})
    @Query("""
            select r from Report r
            where (:status is null or r.status = :status)
              and (:keyword is null
                   or lower(r.post.title) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(r.reason) like lower(concat('%', cast(:keyword as string), '%')))
            order by r.reportId desc
            """)
    Page<Report> search(@Param("status") ReportStatus status,
                        @Param("keyword") String keyword,
                        Pageable pageable);

    @EntityGraph(attributePaths = {"post"})
    List<Report> findAllByReporter_UserIdOrderByCreatedAtDesc(Long userId);
}
