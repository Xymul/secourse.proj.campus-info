package org.secourse.hzau.campus.service;

import org.secourse.hzau.campus.common.BizException;
import org.secourse.hzau.campus.common.PageResult;
import org.secourse.hzau.campus.config.AppProperties;
import org.secourse.hzau.campus.dto.AdminDtos;
import org.secourse.hzau.campus.dto.AuthDtos;
import org.secourse.hzau.campus.dto.PostDtos;
import org.secourse.hzau.campus.entity.AppUser;
import org.secourse.hzau.campus.entity.enums.UserRole;
import org.secourse.hzau.campus.entity.enums.UserStatus;
import org.secourse.hzau.campus.entity.enums.ClaimStatus;
import org.secourse.hzau.campus.entity.enums.PostStatus;
import org.secourse.hzau.campus.entity.enums.ReportStatus;
import org.secourse.hzau.campus.repository.AppUserRepository;
import org.secourse.hzau.campus.repository.AuthTokenRepository;
import org.secourse.hzau.campus.repository.CampusLocationRepository;
import org.secourse.hzau.campus.repository.CategoryRepository;
import org.secourse.hzau.campus.repository.ClaimRepository;
import org.secourse.hzau.campus.repository.OperationLogRepository;
import org.secourse.hzau.campus.repository.PostRepository;
import org.secourse.hzau.campus.repository.ReportRepository;
import org.secourse.hzau.campus.repository.TaskRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台运营管理服务（F6）：仪表盘、数据分析、用户管理、日志与任务记录。
 */
@Service
public class AdminService {

    private final PostRepository postRepository;
    private final AppUserRepository appUserRepository;
    private final CampusLocationRepository locationRepository;
    private final CategoryRepository categoryRepository;
    private final ReportRepository reportRepository;
    private final ClaimRepository claimRepository;
    private final TaskRecordRepository taskRecordRepository;
    private final OperationLogRepository operationLogRepository;
    private final AuthTokenRepository authTokenRepository;
    private final GovernanceService governanceService;
    private final PostService postService;
    private final PostMapper postMapper;
    private final OperationLogService operationLogService;
    private final AppProperties properties;

    public AdminService(PostRepository postRepository,
                        AppUserRepository appUserRepository,
                        CampusLocationRepository locationRepository,
                        CategoryRepository categoryRepository,
                        ReportRepository reportRepository,
                        ClaimRepository claimRepository,
                        TaskRecordRepository taskRecordRepository,
                        OperationLogRepository operationLogRepository,
                        AuthTokenRepository authTokenRepository,
                        GovernanceService governanceService,
                        PostService postService,
                        PostMapper postMapper,
                        OperationLogService operationLogService,
                        AppProperties properties) {
        this.postRepository = postRepository;
        this.appUserRepository = appUserRepository;
        this.locationRepository = locationRepository;
        this.categoryRepository = categoryRepository;
        this.reportRepository = reportRepository;
        this.claimRepository = claimRepository;
        this.taskRecordRepository = taskRecordRepository;
        this.operationLogRepository = operationLogRepository;
        this.authTokenRepository = authTokenRepository;
        this.governanceService = governanceService;
        this.postService = postService;
        this.postMapper = postMapper;
        this.operationLogService = operationLogService;
        this.properties = properties;
    }

    // ------------------------------------------------------------- 仪表盘

    @Transactional(readOnly = true)
    public AdminDtos.Dashboard dashboard(Long schoolId) {
        Map<String, Long> statusCounts = statusCounts(schoolId);
        long todayNew = schoolId == null
                ? postRepository.findCreatedAtSince(null, LocalDate.now().atStartOfDay()).size()
                : postRepository.countBySchool_SchoolIdAndCreatedAtAfter(schoolId, LocalDate.now().atStartOfDay());
        long flagged = postRepository.countByVerifyFalseCountGreaterThanEqualOrReportCountGreaterThanEqual(
                properties.getGovernance().getFalseVerifyWarnThreshold(),
                properties.getGovernance().getReportAutoAuditThreshold());
        List<AdminDtos.TaskRecordView> tasks = taskRecordRepository.findTop20ByOrderByTaskIdDesc().stream()
                .limit(5).map(postMapper::toTaskView).toList();
        List<AdminDtos.OperationLogView> logs = operationLogRepository
                .search(null, null, null, PageRequest.of(0, 8,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "logId")))
                .getContent().stream().map(postMapper::toLogView).toList();

        return new AdminDtos.Dashboard(
                sum(statusCounts),
                statusCounts.getOrDefault(PostStatus.PUBLISHED.name(), 0L),
                statusCounts.getOrDefault(PostStatus.PENDING.name(), 0L),
                statusCounts.getOrDefault(PostStatus.EXPIRED.name(), 0L),
                statusCounts.getOrDefault(PostStatus.REMOVED.name(), 0L),
                statusCounts.getOrDefault(PostStatus.RESOLVED.name(), 0L),
                appUserRepository.count(),
                appUserRepository.countByStatus(UserStatus.ACTIVE),
                locationRepository.count(),
                categoryRepository.count(),
                reportRepository.countByStatus(ReportStatus.PENDING),
                claimRepository.countByStatus(ClaimStatus.PENDING),
                todayNew,
                flagged,
                distribution(postRepository.countGroupByCategory(schoolId, PostStatus.PUBLISHED)),
                distribution(postRepository.countGroupBySchool()),
                dailyTrend(schoolId, 7),
                distribution(postRepository.countGroupByLocation(schoolId, PageRequest.of(0, 6))),
                tasks,
                logs);
    }

    private long sum(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private Map<String, Long> statusCounts(Long schoolId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : postRepository.countGroupByStatus(schoolId)) {
            counts.put(((PostStatus) row[0]).name(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    /** 近 N 天的新增信息趋势 */
    private List<Map<String, Object>> dailyTrend(Long schoolId, int days) {
        List<LocalDateTime> created = postRepository.findCreatedAtSince(schoolId,
                LocalDate.now().minusDays(days - 1L).atStartOfDay());
        Map<String, Long> counter = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            counter.put(LocalDate.now().minusDays(i).toString(), 0L);
        }
        for (LocalDateTime time : created) {
            String key = time.toLocalDate().toString();
            counter.computeIfPresent(key, (k, v) -> v + 1);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        counter.forEach((date, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date);
            row.put("count", count);
            trend.add(row);
        });
        return trend;
    }

    private List<Map<String, Object>> distribution(List<Object[]> rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", row[0] == null ? "未分类" : row[0].toString());
            map.put("count", ((Number) row[1]).longValue());
            list.add(map);
        }
        return list;
    }

    // ------------------------------------------------------------- 数据分析

    @Transactional(readOnly = true)
    public AdminDtos.Analytics analytics(Long schoolId) {
        Object[] summary = postRepository.summarizeInteractions(schoolId);
        Map<String, Object> interaction = new LinkedHashMap<>();
        interaction.put("viewCount", summary == null ? 0L : ((Number) summary[0]).longValue());
        interaction.put("commentCount", summary == null ? 0L : ((Number) summary[1]).longValue());
        interaction.put("likeCount", summary == null ? 0L : ((Number) summary[2]).longValue());
        interaction.put("verifyTrueCount", summary == null ? 0L : ((Number) summary[3]).longValue());
        interaction.put("verifyFalseCount", summary == null ? 0L : ((Number) summary[4]).longValue());
        interaction.put("aiAssistedPosts", schoolId == null ? 0L
                : postRepository.countByAiAssistedTrueAndSchool_SchoolId(schoolId));

        List<Map<String, Object>> statusDistribution = new ArrayList<>();
        statusCounts(schoolId).forEach((status, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", status);
            row.put("count", count);
            statusDistribution.add(row);
        });

        List<Map<String, Object>> hotKeywords = new ArrayList<>();
        postService.tagFrequency(schoolId, 10).forEach((keyword, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("keyword", keyword);
            row.put("count", count);
            hotKeywords.add(row);
        });

        return new AdminDtos.Analytics(
                distribution(postRepository.countGroupByCategory(schoolId, PostStatus.PUBLISHED)),
                distribution(postRepository.countGroupBySchool()),
                dailyTrend(schoolId, 14),
                distribution(postRepository.countGroupByLocation(schoolId, PageRequest.of(0, 10))),
                statusDistribution,
                interaction,
                hotKeywords);
    }

    // ------------------------------------------------------------- 用户管理

    @Transactional(readOnly = true)
    public PageResult<AuthDtos.UserProfile> users(Long schoolId, String status, String role, String keyword,
                                                  int page, int size) {
        UserStatus userStatus = parseEnum(UserStatus.class, status, "用户状态");
        UserRole userRole = parseEnum(UserRole.class, role, "用户角色");
        var result = appUserRepository.search(schoolId, userStatus, userRole, blankToNull(keyword),
                PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(size, 100))));
        return PageResult.of(result, postMapper::toUserProfile);
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthDtos.UserProfile updateUserStatus(Long userId, String status, String remark) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
        UserStatus target = parseEnum(UserStatus.class, status, "用户状态");
        if (target == null) {
            throw new BizException("请提供用户状态");
        }
        user.setStatus(target);
        appUserRepository.save(user);
        if (target == UserStatus.DISABLED) {
            authTokenRepository.deleteAllByUserId(userId);
        }
        operationLogService.recordCurrent("UPDATE_USER_STATUS", "USER", userId,
                "调整用户状态为 " + target.name() + "（" + user.getUsername() + "）"
                        + (remark == null || remark.isBlank() ? "" : "：" + remark));
        return postMapper.toUserProfile(user);
    }

    // ------------------------------------------------------------- 举报 / 日志 / 任务

    @Transactional(readOnly = true)
    public PageResult<AdminDtos.ReportView> reports(String status, String keyword, int page, int size) {
        ReportStatus reportStatus = parseEnum(ReportStatus.class, status, "举报状态");
        var result = reportRepository.search(reportStatus, blankToNull(keyword),
                PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(size, 100))));
        return PageResult.of(result, governanceService::toReportView);
    }

    @Transactional(readOnly = true)
    public PageResult<AdminDtos.OperationLogView> logs(String action, Long operatorId, String keyword,
                                                       int page, int size) {
        var result = operationLogRepository.search(blankToNull(action), operatorId, blankToNull(keyword),
                PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(size, 200))));
        return PageResult.of(result, postMapper::toLogView);
    }

    @Transactional(readOnly = true)
    public PageResult<AdminDtos.TaskRecordView> tasks(int page, int size) {
        var result = taskRecordRepository.findAllByOrderByTaskIdDesc(
                PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(size, 100))));
        return PageResult.of(result, postMapper::toTaskView);
    }

    /** 高频证伪 / 被多次举报的信息，用于后台重点监控 */
    @Transactional(readOnly = true)
    public List<PostDtos.Summary> flaggedPosts(int limit) {
        return postRepository.findFlagged(
                        properties.getGovernance().getFalseVerifyWarnThreshold(),
                        properties.getGovernance().getReportAutoAuditThreshold(),
                        PageRequest.of(0, Math.max(1, Math.min(limit, 50))))
                .stream().map(postMapper::toSummary).toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(label + "取值不合法：" + value);
        }
    }



}
