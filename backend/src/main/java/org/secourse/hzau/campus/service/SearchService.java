package org.secourse.hzau.campus.service;

import org.secourse.hzau.campus.common.PageResult;
import org.secourse.hzau.campus.config.AppProperties;
import org.secourse.hzau.campus.dto.PostDtos;
import org.secourse.hzau.campus.dto.SearchDtos;
import org.secourse.hzau.campus.entity.CampusLocation;
import org.secourse.hzau.campus.entity.Category;
import org.secourse.hzau.campus.entity.Post;
import org.secourse.hzau.campus.repository.PostRepository;
import org.secourse.hzau.campus.search.SearchHit;
import org.secourse.hzau.campus.search.SearchIntent;
import org.secourse.hzau.campus.search.SearchIntentParser;
import org.secourse.hzau.campus.search.SimpleSearchEngine;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信息搜索服务（F2）：
 *
 * <ul>
 *   <li>普通搜索：基于数据库的标题/正文/标签关键词匹配，响应快；</li>
 *   <li>AI 智能搜索：自然语言意图解析 + 自实现相关性引擎，输出匹配原因与降级说明。</li>
 * </ul>
 */
@Service
public class SearchService {

    private static final int CANDIDATE_LIMIT = 1200;

    private final PostRepository postRepository;
    private final MetaService metaService;
    private final PostService postService;
    private final PostMapper postMapper;
    private final SimpleSearchEngine searchEngine = new SimpleSearchEngine();
    private final AppProperties properties;

    public SearchService(PostRepository postRepository,
                         MetaService metaService,
                         PostService postService,
                         PostMapper postMapper,
                         AppProperties properties) {
        this.postRepository = postRepository;
        this.metaService = metaService;
        this.postService = postService;
        this.postMapper = postMapper;
        this.properties = properties;
    }

    /** 普通关键词搜索 */
    @Transactional(readOnly = true)
    public SearchDtos.KeywordSearchResponse keywordSearch(String keyword, Long schoolId, Long categoryId,
                                                          Long locationId, int page, int size) {
        PageResult<PostDtos.Summary> result = postService.list(schoolId, categoryId, locationId, keyword,
                null, null, true, page, size, "hot");
        return new SearchDtos.KeywordSearchResponse(result.getList(), result.getTotal(),
                result.getPage(), result.getSize(), "keyword");
    }

    /** AI 智能搜索（自实现算法版本） */
    @Transactional(readOnly = true)
    public SearchDtos.AiSearchResponse aiSearch(String query, Long schoolId, Double lat, Double lng,
                                                Double radiusKm, Long categoryId, int limit) {
        int max = Math.max(1, Math.min(limit, 60));
        List<Category> categories = metaService.enabledCategories();
        List<CampusLocation> locations = metaService.schoolLocations(schoolId);
        double defaultRadius = properties.getSearch().getDefaultRadiusKm();
        SearchIntent intent = SearchIntentParser.parse(query, categories, locations, defaultRadius);
        if (intent.getRadiusKm() == null) {
            intent.setRadiusKm(radiusKm == null ? defaultRadius : radiusKm);
        }
        if (categoryId != null) {
            categories.stream()
                    .filter(category -> categoryId.equals(category.getCategoryId()))
                    .findFirst()
                    .ifPresent(category -> {
                        intent.setCategoryCode(category.getCode());
                        intent.setCategoryName(category.getName());
                    });
        }

        List<Post> candidates = schoolId == null
                ? List.of()
                : postRepository.findSearchCandidates(schoolId, LocalDateTime.now(), PageRequest.of(0, CANDIDATE_LIMIT));
        SimpleSearchEngine.Context context = new SimpleSearchEngine.Context(
                properties.getSearch().getFreshnessHalfLifeDays(),
                intent.getRadiusKm() == null ? defaultRadius : intent.getRadiusKm(),
                lat, lng);

        SimpleSearchEngine.Result result = searchEngine.search(candidates, intent, context);
        List<SearchHit> hits = result.hits();
        boolean fallback = result.fallback();
        if (hits.isEmpty()) {
            hits = searchEngine.hotList(candidates, max);
            fallback = true;
        }
        List<PostDtos.Summary> list = new ArrayList<>(hits.stream()
                .filter(hit -> categoryId == null || (hit.getPost().getCategory() != null
                        && categoryId.equals(hit.getPost().getCategory().getCategoryId())))
                .limit(max)
                .map(postMapper::toSummary)
                .toList());
        if (list.isEmpty() && !candidates.isEmpty()) {
            list = new ArrayList<>(searchEngine.hotList(candidates, max).stream().map(postMapper::toSummary).toList());
            fallback = true;
        }

        return new SearchDtos.AiSearchResponse(query, intent.toSummary(), list, list.size(),
                result.appliedConditions(), result.relaxedConditions(), fallback,
                buildExplanation(intent, result, fallback), "ai");
    }

    /** 生成可读的检索说明，让用户知道系统「理解」了什么 */
    private String buildExplanation(SearchIntent intent, SimpleSearchEngine.Result result, boolean fallback) {
        StringBuilder sb = new StringBuilder();
        if (!intent.isUnderstood()) {
            sb.append("未能完全理解您的描述，已按关键词为您检索。");
        } else {
            List<String> parts = new ArrayList<>();
            if (intent.getCategoryName() != null) {
                parts.add("分类=" + intent.getCategoryName());
            }
            if (intent.getLocationName() != null) {
                parts.add("地点=" + intent.getLocationName());
            }
            if (intent.getTimeLabel() != null) {
                parts.add("时间=" + intent.getTimeLabel());
            }
            if (intent.isNearby()) {
                parts.add("距离=" + intent.getRadiusKm() + " 公里内");
            }
            if (intent.getMaxPrice() != null) {
                parts.add("预算≤" + intent.getMaxPrice().intValue() + " 元");
            }
            if (!intent.getKeywords().isEmpty()) {
                parts.add("关键词=" + String.join("、", intent.getKeywords()));
            }
            sb.append("已理解您的需求：").append(String.join("；", parts)).append("。");
        }
        if (!result.relaxedConditions().isEmpty()) {
            sb.append("未找到完全匹配的结果，已自动放宽条件（")
                    .append(String.join("、", result.relaxedConditions())).append("）。");
        }
        if (fallback) {
            sb.append("以下为平台近期热门校园信息，供您参考。");
        }
        return sb.toString();
    }

    /** 热门信息（首页与降级推荐使用） */
    @Transactional(readOnly = true)
    public List<PostDtos.Summary> hotPosts(Long schoolId, Long categoryId, int limit) {
        return postService.list(schoolId, categoryId, null, null, null, null, true, 1,
                Math.max(1, Math.min(limit, 50)), "hot").getList();
    }

    /** 搜索推荐词：取近期信息的高频标签 */
    @Transactional(readOnly = true)
    public List<SearchDtos.HotKeyword> hotKeywords(Long schoolId, int limit) {
        Map<String, Integer> frequency = postService.tagFrequency(schoolId, limit);
        List<SearchDtos.HotKeyword> keywords = new ArrayList<>();
        frequency.forEach((keyword, count) -> keywords.add(new SearchDtos.HotKeyword(keyword, count)));
        if (keywords.isEmpty()) {
            keywords.add(new SearchDtos.HotKeyword("失物招领", 0));
            keywords.add(new SearchDtos.HotKeyword("二手自行车", 0));
            keywords.add(new SearchDtos.HotKeyword("图书馆 自习", 0));
            keywords.add(new SearchDtos.HotKeyword("组队跑步", 0));
        }
        return keywords;
    }

    /** 搜索引擎能力说明（供前端展示检索模式） */
    @Transactional(readOnly = true)
    public Map<String, Object> searchMeta() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("engine", "self-implemented-bm25-variant");
        map.put("freshnessHalfLifeDays", properties.getSearch().getFreshnessHalfLifeDays());
        map.put("defaultRadiusKm", properties.getSearch().getDefaultRadiusKm());
        map.put("features", List.of("中文分词", "分类/地点/时间/价格/距离意图解析", "相关性排序", "匹配原因解释", "条件自动放宽"));
        map.put("note", "当前为规则与统计实现，后续可平滑替换为大模型/向量检索");
        return map;
    }

}
