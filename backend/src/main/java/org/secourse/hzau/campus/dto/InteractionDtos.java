package org.secourse.hzau.campus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 互动、协同验证、举报与认领相关对象。
 */
public final class InteractionDtos {

    private InteractionDtos() {
    }

    public record CommentRequest(
            @NotBlank(message = "评论内容不能为空") @Size(max = 1000, message = "评论不超过1000字") String content
    ) {
    }

    public record CommentView(
            Long commentId,
            String content,
            Long authorId,
            String authorNickname,
            LocalDateTime createdAt
    ) {
    }

    public record VerifyRequest(
            @NotBlank(message = "验证结果不能为空") String result,
            @Size(max = 255, message = "备注不超过255字") String remark
    ) {
    }

    /** 协同验证汇总：证实/证伪占比，供前端进度条展示 */
    public record VerificationSummary(
            long trueCount,
            long falseCount,
            long unknownCount,
            int total,
            double trustRatio,
            int trustPercent,
            String myResult,
            String level,
            boolean warned
    ) {
    }

    public record ReportRequest(
            @NotBlank(message = "举报原因不能为空") @Size(max = 100) String reason,
            @Size(max = 500, message = "补充说明不超过500字") String detail
    ) {
    }

    public record ClaimRequest(
            @NotBlank(message = "认领说明不能为空") @Size(max = 500, message = "认领说明不超过500字") String description,
            @Size(max = 100) String contact
    ) {
    }

    public record ClaimView(
            Long claimId,
            Long postId,
            String postTitle,
            Long userId,
            String nickname,
            String description,
            String contact,
            String status,
            String handleRemark,
            LocalDateTime createdAt,
            LocalDateTime handledAt
    ) {
    }

    public record ClaimHandleRequest(
            @NotBlank(message = "处理结果不能为空") String action,
            @Size(max = 255) String remark
    ) {
    }

    public record InteractionResult(boolean active, long total, String message) {
    }

    /** 我的互动汇总 */
    public record MyInteractions(
            List<PostDtos.Summary> posts,
            List<PostDtos.Summary> likedPosts,
            List<VerificationRecord> verifications,
            List<ClaimView> claims
    ) {
    }

    public record VerificationRecord(
            Long postId,
            String postTitle,
            String result,
            String remark,
            LocalDateTime createdAt
    ) {
    }
}
