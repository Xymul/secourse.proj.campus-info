package org.secourse.hzau.campus.web.admin;

import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.dto.MetaDtos;
import org.secourse.hzau.campus.service.MetaService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台分类管理与地点核验接口（F6）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMetaController {

    private final MetaService metaService;

    public AdminMetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<MetaDtos.CategoryView>> categories(
            @RequestParam(defaultValue = "true") boolean includeDisabled) {
        return ApiResponse.ok(metaService.listCategories(includeDisabled));
    }

    @PostMapping("/categories")
    public ApiResponse<MetaDtos.CategoryView> createCategory(@RequestBody MetaDtos.CategoryRequest request) {
        return ApiResponse.ok(metaService.saveCategory(null, request));
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<MetaDtos.CategoryView> updateCategory(@PathVariable Long categoryId,
                                                             @RequestBody MetaDtos.CategoryRequest request) {
        return ApiResponse.ok(metaService.saveCategory(categoryId, request));
    }

    @GetMapping("/locations")
    public ApiResponse<List<MetaDtos.LocationView>> locations(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(metaService.listLocations(schoolId, keyword, status, null, null));
    }

    @PostMapping("/locations")
    public ApiResponse<MetaDtos.LocationView> createLocation(@RequestBody MetaDtos.LocationRequest request) {
        return ApiResponse.ok(metaService.saveLocation(null, request));
    }

    @PutMapping("/locations/{locationId}")
    public ApiResponse<MetaDtos.LocationView> updateLocation(@PathVariable Long locationId,
                                                             @RequestBody MetaDtos.LocationRequest request) {
        return ApiResponse.ok(metaService.saveLocation(locationId, request));
    }

    @PostMapping("/locations/{locationId}/verify")
    public ApiResponse<Void> verifyLocation(@PathVariable Long locationId,
                                            @RequestParam(defaultValue = "true") boolean approved,
                                            @RequestParam(required = false) String remark) {
        metaService.verifyLocation(locationId, approved, remark);
        return ApiResponse.ok();
    }

    @DeleteMapping("/locations/{locationId}")
    public ApiResponse<Void> deleteLocation(@PathVariable Long locationId) {
        metaService.deleteLocation(locationId);
        return ApiResponse.ok();
    }
}
