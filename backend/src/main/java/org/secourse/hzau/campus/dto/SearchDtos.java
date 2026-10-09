package org.secourse.hzau.campus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 搜索与 AI 辅助相关对象。
 */
public final class SearchDtos {

    private SearchDtos() {
    }

    /** 关键词搜索（普通搜索）结果 */
    public record KeywordSearchResponse(
            List<PostDtos.Summary> list,
            long total,
            int page,
            int size,
            String mode
    ) {
    }

    /** AI 智能搜索结果：意图 + 结果 + 解释 */
    public record AiSearchResponse(
            String query,
            Map<String, Object> intent,
            List<PostDtos.Summary> list,
            int total,
            List<String> appliedConditions,
            List<String> relaxedConditions,
            boolean fallback,
            String explanation,
            String mode
    ) {
    }

    /** AI 辅助发布请求 */
    public record AiSuggestRequest(
            @NotBlank(message = "标题不能为空") @Size(max = 200) String title,
            @Size(max = 4000) String content,
            Long schoolId,
            String selectedCategoryCode
    ) {
    }

    /** 热门/推荐词条 */
    public record HotKeyword(String keyword, int count) {
    }
}
