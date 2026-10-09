package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.Verification;
import org.secourse.hzau.campus.entity.enums.VerificationResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationRepository extends JpaRepository<Verification, Long> {

    Optional<Verification> findByPost_PostIdAndUser_UserId(Long postId, Long userId);

    long countByPost_PostIdAndResult(Long postId, VerificationResult result);

    List<Verification> findAllByPost_PostId(Long postId);

    @EntityGraph(attributePaths = {"post"})
    List<Verification> findAllByUser_UserIdOrderByCreatedAtDesc(Long userId);
}
