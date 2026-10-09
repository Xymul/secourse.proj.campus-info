package org.secourse.hzau.campus.web;

import jakarta.validation.Valid;
import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.dto.SearchDtos;
import org.secourse.hzau.campus.entity.CampusLocation;
import org.secourse.hzau.campus.entity.Category;
import org.secourse.hzau.campus.security.CurrentUser;
import org.secourse.hzau.campus.security.CurrentUserContext;
import org.secourse.hzau.campus.search.AiPublishAdvice;
import org.secourse.hzau.campus.search.AiPublishAdvisor;
import org.secourse.hzau.campus.service.MetaService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 辅助发布接口（F3）：根据标题与正文给出分类、标签、有效期与风险提示建议。
 *
 * <p>当前实现为自实现的规则算法（词典 + 频次 + 阈值），用户可修改全部建议后提交。</p>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final MetaService metaService;

    public AiController(MetaService metaService) {
        this.metaService = metaService;
    }

    @PostMapping("/suggest")
    public ApiResponse<AiPublishAdvice> suggest(@Valid @RequestBody SearchDtos.AiSuggestRequest request) {
        CurrentUser user = CurrentUserContext.require();
        Long schoolId = request.schoolId() != null ? request.schoolId() : user.schoolId();
        List<Category> categories = metaService.enabledCategories();
        List<CampusLocation> locations = metaService.schoolLocations(schoolId);
        return ApiResponse.ok(AiPublishAdvisor.advise(request.title(), request.content(),
                categories, locations, request.selectedCategoryCode()));
    }
}
