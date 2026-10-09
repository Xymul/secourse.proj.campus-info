package org.secourse.hzau.campus.web;

import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.dto.SearchDtos;
import org.secourse.hzau.campus.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 信息搜索接口（F2）：普通关键词搜索与 AI 智能搜索。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** 普通关键词搜索 */
    @GetMapping("/keyword")
    public ApiResponse<SearchDtos.KeywordSearchResponse> keyword(
            @RequestParam String q,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(searchService.keywordSearch(q, schoolId, categoryId, locationId, page, size));
    }

    /** AI 智能搜索：自然语言 -> 意图 -> 相关性结果 + 匹配原因 */
    @GetMapping("/ai")
    public ApiResponse<SearchDtos.AiSearchResponse> ai(
            @RequestParam String q,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(searchService.aiSearch(q, schoolId, lat, lng, radiusKm, categoryId, limit));
    }
}
