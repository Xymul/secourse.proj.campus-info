package org.secourse.hzau.campus.web;

import jakarta.validation.Valid;
import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.common.PageResult;
import org.secourse.hzau.campus.dto.PostDtos;
import org.secourse.hzau.campus.security.CurrentUserContext;
import org.secourse.hzau.campus.service.PostService;
import org.secourse.hzau.campus.service.SearchService;
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
 * 信息节点接口：列表、地图、周边、详情、发布、编辑、下架（F1 / F3）。
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final SearchService searchService;

    public PostController(PostService postService, SearchService searchService) {
        this.postService = postService;
        this.searchService = searchService;
    }

    /** 信息列表（分页 + 筛选 + 排序） */
    @GetMapping
    public ApiResponse<PageResult<PostDtos.Summary>> list(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long authorId,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "latest") String sort) {
        return ApiResponse.ok(postService.list(schoolId, categoryId, locationId, keyword, status, authorId,
                activeOnly, page, size, sort));
    }

    /** 地图视野加载（视窗范围 + 分类筛选 + 同地点聚合） */
    @GetMapping("/map")
    public ApiResponse<List<PostDtos.MapNode>> map(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLng,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(postService.mapNodes(schoolId, minLat, maxLat, minLng, maxLng, categoryId, limit));
    }

    /** 周边 X 公里内的有效信息 */
    @GetMapping("/nearby")
    public ApiResponse<List<PostDtos.Summary>> nearby(
            @RequestParam Long schoolId,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(postService.nearby(schoolId, lat, lng, radiusKm, categoryId, limit));
    }

    /** 热门信息 */
    @GetMapping("/hot")
    public ApiResponse<List<PostDtos.Summary>> hot(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(searchService.hotPosts(schoolId, categoryId, limit));
    }

    /** 我发布的信息 */
    @GetMapping("/mine")
    public ApiResponse<List<PostDtos.Summary>> mine() {
        return ApiResponse.ok(postService.myPosts(CurrentUserContext.requireUserId()));
    }

    /** 同地点信息聚合 */
    @GetMapping("/cluster")
    public ApiResponse<PostDtos.LocationCluster> cluster(@RequestParam Long locationId) {
        return ApiResponse.ok(postService.locationCluster(locationId));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDtos.Detail> detail(@PathVariable Long postId) {
        return ApiResponse.ok(postService.detail(postId));
    }

    @PostMapping
    public ApiResponse<PostDtos.Detail> publish(@Valid @RequestBody PostDtos.CreateRequest request) {
        return ApiResponse.ok(postService.publish(request));
    }

    @PutMapping("/{postId}")
    public ApiResponse<PostDtos.Summary> update(@PathVariable Long postId,
                                                @Valid @RequestBody PostDtos.UpdateRequest request) {
        return ApiResponse.ok(postService.update(postId, request));
    }

    @PostMapping("/{postId}/resolve")
    public ApiResponse<Void> resolve(@PathVariable Long postId) {
        postService.resolve(postId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> remove(@PathVariable Long postId,
                                    @RequestParam(required = false) String reason) {
        postService.remove(postId, reason);
        return ApiResponse.ok();
    }
}
