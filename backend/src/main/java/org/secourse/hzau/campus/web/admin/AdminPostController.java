package org.secourse.hzau.campus.web.admin;

import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.common.PageResult;
import org.secourse.hzau.campus.dto.PostDtos;
import org.secourse.hzau.campus.service.AdminService;
import org.secourse.hzau.campus.service.GovernanceService;
import org.secourse.hzau.campus.service.PostService;
import org.secourse.hzau.campus.dto.AdminDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台内容审核接口（F6）：待审列表、审核操作、异常信息监控。
 */
@RestController
@RequestMapping("/api/admin/posts")
public class AdminPostController {

    private final PostService postService;
    private final GovernanceService governanceService;
    private final AdminService adminService;

    public AdminPostController(PostService postService, GovernanceService governanceService, AdminService adminService) {
        this.postService = postService;
        this.governanceService = governanceService;
        this.adminService = adminService;
    }

    /** 内容列表（默认展示待审核） */
    @GetMapping
    public ApiResponse<PageResult<PostDtos.Summary>> list(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String sort) {
        String effectiveStatus = (status == null || status.isBlank()) ? "PENDING,PUBLISHED,EXPIRED,REMOVED,REJECTED,RESOLVED" : status;
        return ApiResponse.ok(postService.list(schoolId, categoryId, null, keyword, effectiveStatus,
                null, false, page, size, sort));
    }

    @PostMapping("/{postId}/audit")
    public ApiResponse<PostDtos.Summary> audit(@PathVariable Long postId,
                                               @RequestBody AdminDtos.AuditRequest request) {
        return ApiResponse.ok(governanceService.audit(postId, request));
    }

    /** 高频证伪 / 被多次举报的信息（后台重点监控） */
    @GetMapping("/flagged")
    public ApiResponse<List<PostDtos.Summary>> flagged(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(adminService.flaggedPosts(limit));
    }

    /** 审核状态统计，便于后台顶部卡片展示 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestParam(required = false) Long schoolId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pending", postService.list(schoolId, null, null, null, "PENDING", null, false, 1, 1, "latest").getTotal());
        map.put("published", postService.list(schoolId, null, null, null, "PUBLISHED", null, false, 1, 1, "latest").getTotal());
        map.put("removed", postService.list(schoolId, null, null, null, "REMOVED", null, false, 1, 1, "latest").getTotal());
        map.put("expired", postService.list(schoolId, null, null, null, "EXPIRED", null, false, 1, 1, "latest").getTotal());
        return ApiResponse.ok(map);
    }
}
