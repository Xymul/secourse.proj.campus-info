package org.secourse.hzau.campus.web;

import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.dto.MetaDtos;
import org.secourse.hzau.campus.dto.SearchDtos;
import org.secourse.hzau.campus.service.MetaService;
import org.secourse.hzau.campus.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 公共基础数据接口：学校（多租户）、分类、地点、搜索元信息。
 */
@RestController
@RequestMapping("/api")
public class MetaController {

    private final MetaService metaService;
    private final SearchService searchService;

    public MetaController(MetaService metaService, SearchService searchService) {
        this.metaService = metaService;
        this.searchService = searchService;
    }

    @GetMapping("/schools")
    public ApiResponse<List<MetaDtos.SchoolView>> schools() {
        return ApiResponse.ok(metaService.listSchools());
    }

    @GetMapping("/categories")
    public ApiResponse<List<MetaDtos.CategoryView>> categories(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(metaService.listCategories(includeDisabled));
    }

    @GetMapping("/locations")
    public ApiResponse<List<MetaDtos.LocationView>> locations(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return ApiResponse.ok(metaService.listLocations(schoolId, keyword, status, lat, lng));
    }

    @GetMapping("/schools/{schoolId}/locations")
    public ApiResponse<List<MetaDtos.LocationView>> schoolLocations(@PathVariable Long schoolId) {
        return ApiResponse.ok(metaService.listLocations(schoolId, null, null, null, null));
    }

    @GetMapping("/meta/search-engine")
    public ApiResponse<Map<String, Object>> searchEngine() {
        return ApiResponse.ok(searchService.searchMeta());
    }

    @GetMapping("/meta/hot-keywords")
    public ApiResponse<List<SearchDtos.HotKeyword>> hotKeywords(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(searchService.hotKeywords(schoolId, limit));
    }
}
