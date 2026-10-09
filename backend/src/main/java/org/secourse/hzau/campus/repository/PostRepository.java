package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.Post;
import org.secourse.hzau.campus.entity.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    /** 列表/检索场景统一预取关联对象，避免 N+1 查询 */
    @Override
    @EntityGraph(attributePaths = {"category", "location", "author", "school"})
    Page<Post> findAll(Specification<Post> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "location", "author", "school"})
    List<Post> findAllByPostIdIn(Collection<Long> postIds);

    @EntityGraph(attributePaths = {"category", "location", "author", "school"})
    @Query("select p from Post p where p.postId = :postId")
    Post findDetailByPostId(@Param("postId") Long postId);

    /** 搜索候选集：某校已发布且未过期的信息节点（自实现搜索算法在内存中打分排序） */
    @EntityGraph(attributePaths = {"category", "location", "author"})
    @Query("""
            select p from Post p
            where p.school.schoolId = :schoolId
              and p.status = org.secourse.hzau.campus.entity.enums.PostStatus.PUBLISHED
              and p.expireAt > :now
            order by p.createdAt desc
            """)
    List<Post> findSearchCandidates(@Param("schoolId") Long schoolId,
                                    @Param("now") LocalDateTime now,
                                    Pageable pageable);

    @EntityGraph(attributePaths = {"category", "location", "author"})
    List<Post> findAllByAuthor_UserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"category", "location", "author", "school"})
    List<Post> findAllByLocation_LocationId(Long locationId);

    Page<Post> findAllByAuthor_UserId(Long userId, Pageable pageable);

    long countByStatus(PostStatus status);

    long countByCategory_CategoryId(Long categoryId);

    long countBySchool_SchoolId(Long schoolId);

    long countBySchool_SchoolIdAndStatus(Long schoolId, PostStatus status);

    List<Post> findAllByStatusAndExpireAtBefore(PostStatus status, LocalDateTime time);

    List<Post> findAllByStatusInAndExpireAtBefore(Collection<PostStatus> statuses, LocalDateTime time);

    /** 高频证伪 / 被举报的异常帖子，供后台关注列表使用 */
    @EntityGraph(attributePaths = {"category", "location", "author"})
    @Query("""
            select p from Post p
            where p.verifyFalseCount >= :falseCount or p.reportCount >= :reportCount
            order by p.verifyFalseCount desc, p.reportCount desc
            """)
    List<Post> findFlagged(@Param("falseCount") int falseCount,
                           @Param("reportCount") int reportCount,
                           Pageable pageable);

    @Query("select p.createdAt from Post p where (:schoolId is null or p.school.schoolId = :schoolId) and p.createdAt >= :since")
    List<LocalDateTime> findCreatedAtSince(@Param("schoolId") Long schoolId, @Param("since") LocalDateTime since);

    /** 每个地点的信息数量，用于地点聚合与后台地点核验 */
    @Query("select p.location.locationId, count(p) from Post p where p.location is not null group by p.location.locationId")
    List<Object[]> countGroupByLocationId();

    long countByLocation_LocationId(Long locationId);

    long countBySchool_SchoolIdAndCreatedAtAfter(Long schoolId, LocalDateTime since);

    long countByVerifyFalseCountGreaterThanEqualOrReportCountGreaterThanEqual(int falseCount, int reportCount);

    @Query("""
            select c.name, count(p) from Post p join p.category c
            where (:schoolId is null or p.school.schoolId = :schoolId) and p.status = :status
            group by c.name order by count(p) desc
            """)
    List<Object[]> countGroupByCategory(@Param("schoolId") Long schoolId, @Param("status") PostStatus status);

    @Query("select s.name, count(p) from Post p join p.school s group by s.name order by count(p) desc")
    List<Object[]> countGroupBySchool();

    @Query("""
            select l.name, count(p) from Post p join p.location l
            where (:schoolId is null or p.school.schoolId = :schoolId) and p.location is not null
            group by l.name order by count(p) desc
            """)
    List<Object[]> countGroupByLocation(@Param("schoolId") Long schoolId, Pageable pageable);

    /** 互动数据汇总：浏览量/评论数/点赞数/证实数/证伪数 */
    @Query("""
            select coalesce(sum(p.viewCount), 0), coalesce(sum(p.commentCount), 0),
                   coalesce(sum(p.likeCount), 0), coalesce(sum(p.verifyTrueCount), 0),
                   coalesce(sum(p.verifyFalseCount), 0)
            from Post p
            where (:schoolId is null or p.school.schoolId = :schoolId)
            """)
    Object[] summarizeInteractions(@Param("schoolId") Long schoolId);

    /** 参与过「AI 辅助发布」的信息数量 */
    long countByAiAssistedTrueAndSchool_SchoolId(Long schoolId);

    /** 按状态统计（用于后台状态分布） */
    @Query("select p.status, count(p) from Post p where (:schoolId is null or p.school.schoolId = :schoolId) group by p.status")
    List<Object[]> countGroupByStatus(@Param("schoolId") Long schoolId);

    @Modifying
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.postId = :postId")
    int increaseViewCount(@Param("postId") Long postId);
}
