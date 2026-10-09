package org.secourse.hzau.campus.service;

import org.secourse.hzau.campus.dto.AdminDtos;
import org.secourse.hzau.campus.dto.AuthDtos;
import org.secourse.hzau.campus.dto.InteractionDtos;
import org.secourse.hzau.campus.dto.MetaDtos;
import org.secourse.hzau.campus.dto.PostDtos;
import org.secourse.hzau.campus.entity.AppUser;
import org.secourse.hzau.campus.entity.CampusLocation;
import org.secourse.hzau.campus.entity.Category;
import org.secourse.hzau.campus.entity.Claim;
import org.secourse.hzau.campus.entity.Comment;
import org.secourse.hzau.campus.entity.OperationLog;
import org.secourse.hzau.campus.entity.Post;
import org.secourse.hzau.campus.entity.School;
import org.secourse.hzau.campus.entity.TaskRecord;
import org.secourse.hzau.campus.entity.enums.PostStatus;
import org.secourse.hzau.campus.search.SearchHit;
import org.secourse.hzau.campus.search.TextUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 实体到视图对象的映射：统一处理摘要裁剪、标签拆分与联系方式脱敏。
 */
@Component
public class PostMapper {

    public PostDtos.Summary toSummary(Post post) {
        return buildSummary(post, null, null, null);
    }

    /** 检索结果：附带相关度、距离与匹配原因 */
    public PostDtos.Summary toSummary(SearchHit hit) {
        return buildSummary(hit.getPost(), hit.getRelevance(), hit.getDistanceKm(), hit.getReasons());
    }

    public PostDtos.Summary toSummary(Post post, Double distanceKm) {
        return buildSummary(post, null, distanceKm, null);
    }

    private PostDtos.Summary buildSummary(Post post, Integer relevance, Double distanceKm, List<String> reasons) {
        Category category = post.getCategory();
        CampusLocation location = post.getLocation();
        AppUser author = post.getAuthor();
        return new PostDtos.Summary(
                post.getPostId(),
                post.getTitle(),
                TextUtils.truncate(post.getContent(), 120),
                post.getStatus() == null ? null : post.getStatus().name(),
                category == null ? null : category.getCategoryId(),
                category == null ? null : category.getName(),
                category == null ? null : category.getCode(),
                category == null ? null : category.getColor(),
                location == null ? null : location.getLocationId(),
                location == null ? null : location.getName(),
                post.getLatitude(),
                post.getLongitude(),
                author == null ? null : author.getUserId(),
                author == null ? null : author.getNickname(),
                splitTags(post.getTags()),
                post.getExpireAt(),
                post.getCreatedAt(),
                post.getVerifyTrueCount(),
                post.getVerifyFalseCount(),
                post.getViewCount(),
                post.getCommentCount(),
                post.getLikeCount(),
                post.trustRatio() < 0 ? null : post.trustRatio(),
                distanceKm,
                relevance,
                reasons,
                post.getStatus() == PostStatus.RESOLVED);
    }

    /** 详情：联系方式按权限脱敏展示 */
    public PostDtos.Detail toDetail(Post post, boolean contactVisible, boolean aiAssisted) {
        String contact = post.getContact();
        boolean masked = !contactVisible && contact != null && !contact.isBlank();
        String shown = contact == null || contact.isBlank()
                ? null
                : (masked ? TextUtils.maskContact(contact) : contact);
        return new PostDtos.Detail(toSummary(post), post.getContent(), shown, masked, post.getImageUrl(),
                aiAssisted, post.getAuditRemark(), post.getUpdatedAt());
    }

    public PostDtos.MapNode toMapNode(Post post, Integer nodeCount) {
        Category category = post.getCategory();
        CampusLocation location = post.getLocation();
        return new PostDtos.MapNode(post.getPostId(), post.getTitle(),
                category == null ? null : category.getCode(),
                category == null ? null : category.getName(),
                category == null ? null : category.getColor(),
                location == null ? null : location.getLocationId(),
                location == null ? null : location.getName(),
                post.getLatitude(), post.getLongitude(),
                post.getStatus() == null ? null : post.getStatus().name(),
                post.getVerifyTrueCount(), post.getVerifyFalseCount(), post.getCommentCount(),
                post.getCreatedAt(), nodeCount);
    }

    public MetaDtos.SchoolView toSchoolView(School school) {
        return new MetaDtos.SchoolView(school.getSchoolId(), school.getCode(), school.getName(), school.getCity(),
                school.getCenterLatitude(), school.getCenterLongitude(), school.getDefaultZoom(),
                school.getStatus() == null ? null : school.getStatus().name(),
                Boolean.TRUE.equals(school.getIsTestData()), school.getSortOrder());
    }

    public MetaDtos.CategoryView toCategoryView(Category category, Long postCount) {
        return new MetaDtos.CategoryView(category.getCategoryId(), category.getCode(), category.getName(),
                category.getDescription(), category.getIcon(), category.getColor(), category.getBaseValidDays(),
                category.getSortOrder(), category.getStatus() == null ? null : category.getStatus().name(), postCount);
    }

    public MetaDtos.LocationView toLocationView(CampusLocation location, Long postCount, Double distanceKm) {
        return new MetaDtos.LocationView(location.getLocationId(),
                location.getSchool() == null ? null : location.getSchool().getSchoolId(),
                location.getSchool() == null ? null : location.getSchool().getName(),
                location.getName(), location.getArea(), location.getLatitude(), location.getLongitude(),
                location.getDescription(),
                location.getStatus() == null ? null : location.getStatus().name(), postCount, distanceKm);
    }

    public AuthDtos.UserProfile toUserProfile(AppUser user) {
        return new AuthDtos.UserProfile(user.getUserId(), user.getUsername(), user.getNickname(), user.getAvatar(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getSchool() == null ? null : user.getSchool().getSchoolId(),
                user.getSchool() == null ? null : user.getSchool().getName(),
                user.getCreditScore(), user.getContact());
    }

    public InteractionDtos.CommentView toCommentView(Comment comment) {
        return new InteractionDtos.CommentView(comment.getCommentId(), comment.getContent(),
                comment.getAuthor() == null ? null : comment.getAuthor().getUserId(),
                comment.getAuthor() == null ? null : comment.getAuthor().getNickname(),
                comment.getCreatedAt());
    }

    /** 认领申请视图：联系方式脱敏展示 */
    public InteractionDtos.ClaimView toClaimView(Claim claim) {
        return new InteractionDtos.ClaimView(claim.getClaimId(),
                claim.getPost() == null ? null : claim.getPost().getPostId(),
                claim.getPost() == null ? null : claim.getPost().getTitle(),
                claim.getClaimant() == null ? null : claim.getClaimant().getUserId(),
                claim.getClaimant() == null ? null : claim.getClaimant().getNickname(),
                claim.getDescription(), TextUtils.maskContact(claim.getContact()),
                claim.getStatus() == null ? null : claim.getStatus().name(),
                claim.getHandleRemark(), claim.getCreatedAt(), claim.getHandledAt());
    }

    public AdminDtos.OperationLogView toLogView(OperationLog log) {
        return new AdminDtos.OperationLogView(log.getLogId(), log.getOperatorId(), log.getOperatorName(),
                log.getAction(), log.getTargetType(), log.getTargetId(), log.getDetail(), log.getResult(),
                log.getCreatedAt());
    }

    public AdminDtos.TaskRecordView toTaskView(TaskRecord task) {
        return new AdminDtos.TaskRecordView(task.getTaskId(), task.getTaskName(), task.getTaskType(),
                task.getStatus() == null ? null : task.getStatus().name(), task.getAffectedCount(),
                task.getMessage(), task.getStartedAt(), task.getFinishedAt());
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
    }
}
