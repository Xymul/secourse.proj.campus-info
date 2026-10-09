package org.secourse.hzau.campus.web.admin;

import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.common.PageResult;
import org.secourse.hzau.campus.dto.AdminDtos;
import org.secourse.hzau.campus.service.AdminService;
import org.secourse.hzau.campus.service.GovernanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台运营数据接口（F6）：仪表盘、数据分析、任务记录、操作日志，以及手动触发定时任务。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminStatController {

    private final AdminService adminService;
    private final GovernanceService governanceService;

    public AdminStatController(AdminService adminService, GovernanceService governanceService) {
        this.adminService = adminService;
        this.governanceService = governanceService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDtos.Dashboard> dashboard(@RequestParam(required = false) Long schoolId) {
        return ApiResponse.ok(adminService.dashboard(schoolId));
    }

    @GetMapping("/analytics")
    public ApiResponse<AdminDtos.Analytics> analytics(@RequestParam(required = false) Long schoolId) {
        return ApiResponse.ok(adminService.analytics(schoolId));
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResult<AdminDtos.TaskRecordView>> tasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(adminService.tasks(page, size));
    }

    @GetMapping("/logs")
    public ApiResponse<PageResult<AdminDtos.OperationLogView>> logs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.logs(action, operatorId, keyword, page, size));
    }

    /** 手动触发一次过期治理任务，便于演示与验证 */
    @PostMapping("/tasks/expire")
    public ApiResponse<AdminDtos.TaskRecordView> triggerExpireTask() {
        Long taskId = governanceService.startTask("过期信息自动治理（手动触发）", "POST_EXPIRE");
        int affected = governanceService.expirePosts();
        governanceService.finishTask(taskId, org.secourse.hzau.campus.entity.enums.TaskStatus.SUCCESS,
                affected, "手动触发完成，处理 " + affected + " 条过期信息");
        return ApiResponse.ok(adminService.tasks(1, 1).getList().stream().findFirst().orElse(null));
    }
}
