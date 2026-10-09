package org.secourse.hzau.campus.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 后台运营管理相关对象（F6）。
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** 仪表盘统计 */
    public record Dashboard(
            long totalPosts,
            long publishedPosts,
            long pendingPosts,
            long expiredPosts,
            long removedPosts,
            long resolvedPosts,
            long totalUsers,
            long activeUsers,
            long totalLocations,
            long totalCategories,
            long pendingReports,
            long pendingClaims,
            long todayNewPosts,
            long flaggedPosts,
            List<Map<String, Object>> categoryDistribution,
            List<Map<String, Object>> schoolDistribution,
            List<Map<String, Object>> dailyTrend,
            List<Map<String, Object>> topLocations,
            List<TaskRecordView> recentTasks,
            List<OperationLogView> recentLogs
    ) {
    }

    public record OperationLogView(
            Long logId,
            Long operatorId,
            String operatorName,
            String action,
            String targetType,
            Long targetId,
            String detail,
            String result,
            LocalDateTime createdAt
    ) {
    }

    public record TaskRecordView(
            Long taskId,
            String taskName,
            String taskType,
            String status,
            Integer affectedCount,
            String message,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }

    /** 审核操作 */
    public record AuditRequest(String action, String remark) {
    }

    /** 举报记录视图 */
    public record ReportView(
            Long reportId,
            Long postId,
            String postTitle,
            String postStatus,
            Long reporterId,
            String reporterName,
            String reason,
            String detail,
            String status,
            String handleRemark,
            LocalDateTime createdAt,
            LocalDateTime handledAt
    ) {
    }

    /** 用户状态调整 */
    public record UserStatusRequest(String status, String remark) {
    }

    /** 举报处理 */
    public record ReportHandleRequest(String action, String remark) {
    }

    /** 数据分析 */
    public record Analytics(
            List<Map<String, Object>> categoryDistribution,
            List<Map<String, Object>> schoolDistribution,
            List<Map<String, Object>> dailyTrend,
            List<Map<String, Object>> topLocations,
            List<Map<String, Object>> statusDistribution,
            Map<String, Object> interactionSummary,
            List<Map<String, Object>> hotKeywords
    ) {
    }
}
