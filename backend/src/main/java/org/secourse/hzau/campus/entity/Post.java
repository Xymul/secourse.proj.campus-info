package org.secourse.hzau.campus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.secourse.hzau.campus.entity.enums.PostStatus;

import java.time.LocalDateTime;

/**
 * 信息帖子（信息节点）：地图上的核心信息对象。
 */
@Entity
@Table(name = "post")
@Getter
@Setter
@NoArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private CampusLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser author;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, length = 4000)
    private String content;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "contact", length = 100)
    private String contact;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** 标签，英文逗号分隔 */
    @Column(name = "tags", length = 255)
    private String tags;

    /** 是否使用了「AI 辅助发布」建议，用于运营统计 */
    @Column(name = "ai_assisted", nullable = false)
    private Boolean aiAssisted = false;

    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostStatus status = PostStatus.PUBLISHED;

    @Column(name = "verify_true_count", nullable = false)
    private Integer verifyTrueCount = 0;

    @Column(name = "verify_false_count", nullable = false)
    private Integer verifyFalseCount = 0;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "comment_count", nullable = false)
    private Integer commentCount = 0;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;

    @Column(name = "report_count", nullable = false)
    private Integer reportCount = 0;

    /** 热度分：互动 + 验证的综合评分，用于后台预警与排序 */
    @Column(name = "hot_score", nullable = false)
    private Double hotScore = 0d;

    /** 乐观锁版本：协同验证等并发写场景下保证一致性 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "audit_by")
    private Long auditBy;

    @Column(name = "audited_at")
    private LocalDateTime auditedAt;

    @Column(name = "audit_remark", length = 255)
    private String auditRemark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 是否仍然有效（已发布且未过期） */
    @Transient
    public boolean isActive() {
        return status == PostStatus.PUBLISHED && expireAt != null && expireAt.isAfter(LocalDateTime.now());
    }

    /** 可信度：证实占比（无验证时返回 -1 表示暂无数据） */
    @Transient
    public double trustRatio() {
        int total = verifyTrueCount + verifyFalseCount;
        return total == 0 ? -1d : (double) verifyTrueCount / total;
    }
}
