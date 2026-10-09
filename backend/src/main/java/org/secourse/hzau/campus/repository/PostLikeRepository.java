package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPost_PostIdAndUser_UserId(Long postId, Long userId);

    long countByPost_PostId(Long postId);

    List<PostLike> findAllByUser_UserId(Long userId);

    void deleteAllByPost_PostId(Long postId);
}
