package org.secourse.hzau.campus.service;

import org.secourse.hzau.campus.common.BizException;
import org.secourse.hzau.campus.config.AppProperties;
import org.secourse.hzau.campus.dto.AdminDtos;
import org.secourse.hzau.campus.dto.InteractionDtos;
import org.secourse.hzau.campus.entity.Post;
import org.secourse.hzau.campus.entity.Report;
import org.secourse.hzau.campus.entity.TaskRecord;
import org.secourse.hzau.campus.entity.enums.PostStatus;
import org.secourse.hzau.campus.entity.enums.ReportStatus;
import org.secourse.hzau.campus.entity.enums.TaskStatus;
import org.secourse.hzau.campus.repository.PostRepository;
import org.secourse.hzau.campus.repository.ReportRepository;
import org.secourse.hzau.campus.repository.TaskRecordRepository;
import org.secourse.hzau.campus.security.CurrentUser;
import org.secourse.hzau.campus.security.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 信息治理服务（F5）：举报、内容审核、过期治理、操作留痕。
 *
 * <p>事务设计：过期治理使用「任务记录 + 业务处理 + 结果回写」三段独立事务
 * （{@code REQUIRES_NEW}），保证即使批量处理失败，任务轨迹依然完整可查。</p>
 */
@Service
public class GovernanceService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final TaskRecordRepository taskRecordRepository;
    private final PostService postService;
    private final PostMapper postMapper;
    private final OperationLogService operationLogService;
    private final AppProperties properties;

    public GovernanceService(ReportRepository reportRepository,
                             PostRepository postRepository,
                             TaskRecordRepository taskRecordRepository,
                             PostService postService,
                             PostMapper postMapper,
                             OperationLogService operationLogService,
                             AppProperties properties) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.taskRecordRepository = taskRecordRepository;
        this.postService = postService;
        this.postMapper = postMapper;
        this.operationLogService = operationLogService;
        this.properties = properties;
    }

    // ------------------------------------------------------------- 举报

    /** 用户举报：举报记录、帖子举报计数、状态流转在同一事务中完成 */
    @Transactional(rollbackFor = Exception.class)
    public void report(Long postId, InteractionDtos.ReportRequest request) {
        CurrentUser user = CurrentUserContext.require();
        Post post = postService.requirePost(postId);
        if (post.getAuthor() != null && post.getAuthor().getUserId().equals(user.userId())) {
            throw new BizException("不能举报自己发布的信息");
        }
        if (reportRepository.existsByPost_PostIdAndReporter_UserId(postId, user.userId())) {
            throw new BizException("您已举报过该信息，我们会尽快处理");
        }
        Report report = new Report();
        report.setPost(post);
        report.setReporter(entityOf(user));
        report.setReason(request.reason().trim());
        report.setDetail(request.detail());
        report.setStatus(ReportStatus.PENDING);
        reportRepository.save(report);

        post.setReportCount((post.getReportCount() == null ? 0 : post.getReportCount()) + 1);
        if (post.getReportCount() >= properties.getGovernance().getReportAutoAuditThreshold()
                && post.getStatus() == PostStatus.PUBLISHED) {
            post.setStatus(PostStatus.PENDING);
            post.setAuditRemark("举报数达到阈值，自动转入待审核");
        }
        postRepository.save(post);
        operationLogService.record(user.userId(), user.nickname(), "REPORT_POST", "POST", postId,
                "举报信息：" + request.reason());
    }

    private org.secourse.hzau.campus.entity.AppUser entityOf(CurrentUser user) {
        org.secourse.hzau.campus.entity.AppUser entity = new org.secourse.hzau.campus.entity.AppUser();
        entity.setUserId(user.userId());
        return entity;
    }

    /** 管理员处理举报 */
    @Transactional(rollbackFor = Exception.class)
    public AdminDtos.ReportView handleReport(Long reportId, AdminDtos.ReportHandleRequest request) {
        CurrentUser admin = CurrentUserContext.require();
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> BizException.notFound("举报记录不存在"));
        String action = request.action() == null ? "" : request.action().trim().toUpperCase();
        switch (action) {
            case "HANDLE", "HANDLED", "APPROVE" -> {
                report.setStatus(ReportStatus.HANDLED);
                Post post = report.getPost();
                if (post != null && post.getStatus() != PostStatus.REMOVED) {
                    post.setStatus(PostStatus.REMOVED);
                    post.setAuditBy(admin.userId());
                    post.setAuditedAt(LocalDateTime.now());
                    post.setAuditRemark("举报成立：" + report.getReason());
                    postRepository.save(post);
                }
            }
            case "REJECT", "REJECTED" -> report.setStatus(ReportStatus.REJECTED);
            default -> throw new BizException("处理结果不合法：" + request.action());
        }
        report.setHandledBy(admin.userId());
        report.setHandledAt(LocalDateTime.now());
        report.setHandleRemark(request.remark());
        reportRepository.save(report);
        operationLogService.record(admin.userId(), admin.nickname(), "HANDLE_REPORT", "REPORT", reportId,
                "处理举报：" + report.getStatus().name() + (request.remark() == null ? "" : "（" + request.remark() + "）"));
        return toReportView(report);
    }

    public AdminDtos.ReportView toReportView(Report report) {
        Post post = report.getPost();
        return new AdminDtos.ReportView(report.getReportId(),
                post == null ? null : post.getPostId(),
                post == null ? null : post.getTitle(),
                post == null || post.getStatus() == null ? null : post.getStatus().name(),
                report.getReporter() == null ? null : report.getReporter().getUserId(),
                report.getReporter() == null ? null : report.getReporter().getNickname(),
                report.getReason(), report.getDetail(),
                report.getStatus() == null ? null : report.getStatus().name(),
                report.getHandleRemark(), report.getCreatedAt(), report.getHandledAt());
    }

    // ------------------------------------------------------------- 内容审核

    /** 管理员审核：APPROVE 通过 / REJECT 驳回 / REMOVE 下架 / RESTORE 恢复 */
    @Transactional(rollbackFor = Exception.class)
    public org.secourse.hzau.campus.dto.PostDtos.Summary audit(Long postId, AdminDtos.AuditRequest request) {
        CurrentUser admin = CurrentUserContext.require();
        Post post = postService.requirePost(postId);
        String action = request.action() == null ? "" : request.action().trim().toUpperCase();
        PostStatus target = switch (action) {
            case "APPROVE", "PASS" -> PostStatus.PUBLISHED;
            case "REJECT" -> PostStatus.REJECTED;
            case "REMOVE", "TAKEDOWN" -> PostStatus.REMOVED;
            case "RESTORE" -> PostStatus.PUBLISHED;
            case "RESOLVE" -> PostStatus.RESOLVED;
            default -> throw new BizException("审核动作不合法：" + request.action());
        };
        if (target == PostStatus.PUBLISHED && post.getExpireAt() != null
                && post.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException("该信息已过期，请先调整有效期后再通过审核");
        }
        post.setStatus(target);
        post.setAuditBy(admin.userId());
        post.setAuditedAt(LocalDateTime.now());
        post.setAuditRemark(request.remark());
        postRepository.save(post);
        operationLogService.record(admin.userId(), admin.nickname(), "AUDIT_POST", "POST", postId,
                "内容审核：" + action + " -> " + target.name()
                        + (request.remark() == null || request.remark().isBlank() ? "" : "（" + request.remark() + "）"));
        return postMapper.toSummary(post);
    }

    // ------------------------------------------------------------- 过期治理

    /** 开启一条任务记录（独立事务，保证任务轨迹不随业务回滚而丢失） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long startTask(String taskName, String taskType) {
        TaskRecord task = new TaskRecord();
        task.setTaskName(taskName);
        task.setTaskType(taskType);
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        return taskRecordRepository.save(task).getTaskId();
    }

    /**
     * 过期治理批处理：把「已发布且已过有效期」的信息置为 EXPIRED。
     *
     * @return 处理条数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int expirePosts() {
        List<Post> expired = postRepository.findAllByStatusInAndExpireAtBefore(
                List.of(PostStatus.PUBLISHED, PostStatus.PENDING), LocalDateTime.now());
        for (Post post : expired) {
            post.setStatus(PostStatus.EXPIRED);
            post.setAuditRemark("有效期到期，系统自动过期");
        }
        postRepository.saveAll(expired);
        for (Post post : expired) {
            operationLogService.record(null, "系统", "EXPIRE_POST", "POST", post.getPostId(),
                    "自动过期：" + post.getTitle());
        }
        return expired.size();
    }

    /** 回写任务执行结果（独立事务） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishTask(Long taskId, TaskStatus status, int affectedCount, String message) {
        taskRecordRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            task.setAffectedCount(affectedCount);
            task.setMessage(message);
            task.setFinishedAt(LocalDateTime.now());
            taskRecordRepository.save(task);
            operationLogService.record(null, "系统", "TASK_RESULT", "TASK", taskId,
                    task.getTaskName() + "：" + status.name() + "，处理 " + affectedCount + " 条");
        });
    }


}
