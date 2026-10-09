package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.AppUser;
import org.secourse.hzau.campus.entity.enums.UserRole;
import org.secourse.hzau.campus.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = "school")
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(UserRole role);

    long countByStatus(UserStatus status);

    @Query("""
            select u from AppUser u left join fetch u.school s
            where (:schoolId is null or s.schoolId = :schoolId)
              and (:status is null or u.status = :status)
              and (:role is null or u.role = :role)
              and (:keyword is null
                   or lower(u.username) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(u.nickname) like lower(concat('%', cast(:keyword as string), '%')))
            order by u.userId desc
            """)
    Page<AppUser> search(@Param("schoolId") Long schoolId,
                         @Param("status") UserStatus status,
                         @Param("role") UserRole role,
                         @Param("keyword") String keyword,
                         Pageable pageable);

    List<AppUser> findAllByRole(UserRole role);
}
