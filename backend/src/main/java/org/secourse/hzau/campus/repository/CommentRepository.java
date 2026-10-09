package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.Comment;
import org.secourse.hzau.campus.entity.enums.CommonStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = {"author"})
    List<Comment> findAllByPost_PostIdAndStatusOrderByCreatedAtAsc(Long postId, CommonStatus status);

    @EntityGraph(attributePaths = {"author", "post"})
    Page<Comment> findAllByPost_PostId(Long postId, Pageable pageable);

    long countByPost_PostIdAndStatus(Long postId, CommonStatus status);

    @EntityGraph(attributePaths = {"author", "post"})
    @Query("select c from Comment c where c.author.userId = :userId order by c.createdAt desc")
    List<Comment> findByAuthor(@Param("userId") Long userId, Pageable pageable);
}
