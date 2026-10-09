package org.secourse.hzau.campus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子相关的请求与视图对象。
 */
public final class PostDtos {

    private PostDtos() {
    }

    /** 发布请求 */
    public record CreateRequest(
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题不超过200字") String title,
            @NotBlank(message = "正文不能为空") @Size(max = 4000, message = "正文不超过4000字") String content,
            Long schoolId,
            Long categoryId,
            Long locationId,
            Double latitude,
            Double longitude,
            String contact,
            String imageUrl,
            List<String> tags,
            Integer validDays,
            LocalDateTime expireAt,
            /** 是否使用了 AI 建议（用于统计） */
            Boolean aiAssisted
    ) {
    }

    /** 编辑请求 */
    public record UpdateRequest(
            @Size(max = 200, message = "标题不超过200字") String title,
            @Size(max = 4000, message = "正文不超过4000字") String content,
            Long categoryId,
            Long locationId,
            Double latitude,
            Double longitude,
            String contact,
            String imageUrl,
            List<String> tags,
            Integer validDays,
            LocalDateTime expireAt
    ) {
    }

    /** 列表 / 检索结果视图 */
    public record Summary(
            Long postId,
            String title,
            String summary,
            String status,
            Long categoryId,
            String categoryName,
            String categoryCode,
            String categoryColor,
            Long locationId,
            String locationName,
            Double latitude,
            Double longitude,
            Long authorId,
            String authorNickname,
            List<String> tags,
            LocalDateTime expireAt,
            LocalDateTime createdAt,
            Integer verifyTrueCount,
            Integer verifyFalseCount,
            Integer viewCount,
            Integer commentCount,
            Integer likeCount,
            Double trustRatio,
            Double distanceKm,
            Integer relevance,
            List<String> matchReasons,
            Boolean resolved
    ) {
    }

    /** 详情视图 */
    public record Detail(
            Summary base,
            String content,
            String contact,
            boolean contactMasked,
            String imageUrl,
            boolean aiAssisted,
            String auditRemark,
            LocalDateTime updatedAt
    ) {
    }

    /** 地图信息节点 */
    public record MapNode(
            Long postId,
            String title,
            String categoryCode,
            String categoryName,
            String categoryColor,
            Long locationId,
            String locationName,
            Double latitude,
            Double longitude,
            String status,
            Integer verifyTrueCount,
            Integer verifyFalseCount,
            Integer commentCount,
            LocalDateTime createdAt,
            Integer nodeCount
    ) {
    }

    /** 同地点信息聚合 */
    public record LocationCluster(
            Long locationId,
            String locationName,
            Double latitude,
            Double longitude,
            int count,
            List<Summary> posts
    ) {
    }
}
