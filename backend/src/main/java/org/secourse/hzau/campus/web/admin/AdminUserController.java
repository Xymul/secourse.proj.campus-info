package org.secourse.hzau.campus.web.admin;

import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.common.PageResult;
import org.secourse.hzau.campus.dto.AdminDtos;
import org.secourse.hzau.campus.dto.AuthDtos;
import org.secourse.hzau.campus.service.AdminService;
import org.secourse.hzau.campus.service.GovernanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理与举报处理接口（F6）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final AdminService adminService;
    private final GovernanceService governanceService;

    public AdminUserController(AdminService adminService, GovernanceService governanceService) {
        this.adminService = adminService;
        this.governanceService = governanceService;
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<AuthDtos.UserProfile>> users(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(adminService.users(schoolId, status, role, keyword, page, size));
    }

    @PostMapping("/users/{userId}/status")
    public ApiResponse<AuthDtos.UserProfile> updateStatus(@PathVariable Long userId,
                                                          @RequestBody AdminDtos.UserStatusRequest request) {
        return ApiResponse.ok(adminService.updateUserStatus(userId, request.status(), request.remark()));
    }

    @GetMapping("/reports")
    public ApiResponse<PageResult<AdminDtos.ReportView>> reports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(adminService.reports(status, keyword, page, size));
    }

    @PostMapping("/reports/{reportId}/handle")
    public ApiResponse<AdminDtos.ReportView> handleReport(@PathVariable Long reportId,
                                                          @RequestBody AdminDtos.ReportHandleRequest request) {
        return ApiResponse.ok(governanceService.handleReport(reportId, request));
    }
}
