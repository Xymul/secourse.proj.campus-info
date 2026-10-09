package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.Claim;
import org.secourse.hzau.campus.entity.enums.ClaimStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @EntityGraph(attributePaths = {"claimant"})
    List<Claim> findAllByPost_PostIdOrderByCreatedAtDesc(Long postId);

    @EntityGraph(attributePaths = {"post", "post.category"})
    List<Claim> findAllByClaimant_UserIdOrderByCreatedAtDesc(Long userId);

    long countByStatus(ClaimStatus status);

    boolean existsByPost_PostIdAndClaimant_UserId(Long postId, Long userId);
}
