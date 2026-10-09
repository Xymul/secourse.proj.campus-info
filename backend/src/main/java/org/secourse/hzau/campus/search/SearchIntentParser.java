package org.secourse.hzau.campus.search;

import org.secourse.hzau.campus.entity.CampusLocation;
import org.secourse.hzau.campus.entity.Category;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自然语言意图解析器（自实现）：
 *
 * <p>从用户输入中抽取「分类 / 地点 / 时间 / 距离 / 价格 / 关键词」六类条件，
 * 输出结构化 {@link SearchIntent}，并对每条识别结果给出可解释的依据，
 * 便于前端展示「匹配原因」；无法识别时给出降级说明。</p>
 */
public final class SearchIntentParser {

    private SearchIntentParser() {
    }

    /** 构建分词词典：领域分类词 + 地点名 + 校园常用地点词 */
    public static Set<String> buildDictionary(Collection<Category> categories, Collection<CampusLocation> locations) {
        Set<String> dictionary = new LinkedHashSet<>();
        dictionary.addAll(SearchLexicon.PLACE_WORDS);
        SearchLexicon.categoryKeywords().values().forEach(dictionary::addAll);
        dictionary.addAll(SearchLexicon.NEARBY_WORDS);
        dictionary.addAll(SearchLexicon.TIME_WORDS.keySet());
        if (categories != null) {
            for (Category category : categories) {
                dictionary.add(category.getName());
                if (category.getCode() != null) {
                    dictionary.add(category.getCode().toLowerCase());
                }
            }
        }
        if (locations != null) {
            for (CampusLocation location : locations) {
                dictionary.add(location.getName());
                if (location.getArea() != null && location.getArea().length() >= 2) {
                    dictionary.add(location.getArea());
                }
            }
        }
        dictionary.removeIf(word -> word == null || word.length() < 2);
        return dictionary;
    }

    /**
     * 解析自然语言查询。
     *
     * @param rawQuery        用户输入
     * @param categories      平台启用的分类
     * @param locations       当前学校的地点
     * @param defaultRadiusKm 默认周边半径
     */
    public static SearchIntent parse(String rawQuery,
                                     Collection<Category> categories,
                                     Collection<CampusLocation> locations,
                                     double defaultRadiusKm) {
        SearchIntent intent = new SearchIntent();
        intent.setRawQuery(rawQuery == null ? "" : rawQuery.trim());
        if (rawQuery == null || rawQuery.isBlank()) {
            intent.setUnderstood(false);
            intent.setDegradeReason("未输入查询内容");
            return intent;
        }

        Set<String> dictionary = buildDictionary(categories, locations);
        List<String> keywords = new ArrayList<>();
        for (ChineseTokenizer.Token token : ChineseTokenizer.tokenize(rawQuery, dictionary)) {
            if (token.text().length() >= 2 && !keywords.contains(token.text())) {
                keywords.add(token.text());
            }
        }
        intent.setKeywords(keywords);

        // 1) 分类意图
        CategoryGuess guess = classifyCategory(rawQuery, categories);
        intent.setCategoryCode(guess.code());
        intent.setCategoryName(guess.categoryName());
        intent.setCategoryReasons(guess.reasons());

        // 2) 地点意图：优先匹配具体地点名，其次匹配校园通用地点词
        if (locations != null && !locations.isEmpty()) {
            String hit = TextUtils.firstMatched(rawQuery, locations.stream().map(CampusLocation::getName).toList());
            if (hit != null) {
                locations.stream().filter(l -> l.getName().equals(hit)).findFirst().ifPresent(l -> {
                    intent.setLocationId(l.getLocationId());
                    intent.setLocationName(l.getName());
                });
            }
        }
        if (intent.getLocationId() == null && intent.getLocationName() == null) {
            String hit = TextUtils.firstMatched(rawQuery, SearchLexicon.PLACE_WORDS);
            if (hit != null) {
                intent.setLocationName(hit);
            }
        }

        // 3) 时间意图
        for (Map.Entry<String, Integer> entry : SearchLexicon.TIME_WORDS.entrySet()) {
            if (rawQuery.contains(entry.getKey())) {
                LocalDateTime base = entry.getValue() <= 0
                        ? LocalDate.now().atStartOfDay()
                        : LocalDate.now().minusDays(entry.getValue()).atStartOfDay();
                intent.setCreatedAfter(base);
                intent.setTimeLabel(entry.getKey() + "（近" + Math.max(entry.getValue(), 0) + " 天）");
                break;
            }
        }

        // 4) 距离意图
        if (TextUtils.containsAny(rawQuery, SearchLexicon.NEARBY_WORDS)) {
            intent.setNearby(true);
            intent.setRadiusKm(defaultRadiusKm);
            Double explicit = TextUtils.numberBeforeUnit(rawQuery, "公里");
            if (explicit == null) {
                explicit = TextUtils.numberBeforeUnit(rawQuery, "km");
            }
            if (explicit != null && explicit > 0) {
                intent.setRadiusKm(Math.min(explicit, 50d));
            }
        }

        // 5) 价格意图
        Double price = TextUtils.numberBeforeUnit(rawQuery, "元");
        if (price == null) {
            price = TextUtils.numberBeforeUnit(rawQuery, "块");
        }
        if (price != null && price > 0) {
            intent.setMaxPrice(price);
        }

        boolean understood = !keywords.isEmpty() || intent.getCategoryCode() != null
                || intent.getLocationId() != null || intent.getLocationName() != null
                || intent.getCreatedAfter() != null || intent.isNearby() || intent.getMaxPrice() != null;
        intent.setUnderstood(understood);
        if (!understood) {
            intent.setDegradeReason("未能识别出明确条件，已降级为关键词匹配");
        }
        return intent;
    }

    /** 分类猜测结果 */
    public record CategoryGuess(String code, String categoryName, double confidence, List<String> reasons) {
    }

    /**
     * 依据词典命中情况对文本做分类判断（搜索意图识别与「AI 辅助发布」共用）。
     */
    public static CategoryGuess classifyCategory(String text, Collection<Category> categories) {
        if (text == null || text.isBlank()) {
            return new CategoryGuess(null, null, 0d, List.of());
        }
        String normalized = text.toLowerCase();
        String bestCode = null;
        int bestHits = 0;
        List<String> bestReasons = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : SearchLexicon.categoryKeywords().entrySet()) {
            int hits = 0;
            List<String> reasons = new ArrayList<>();
            for (String keyword : entry.getValue()) {
                int occurrences = TextUtils.countOccurrences(normalized, keyword);
                if (occurrences > 0) {
                    hits += occurrences * keyword.length();
                    if (reasons.size() < 3) {
                        reasons.add(keyword);
                    }
                }
            }
            if (hits > bestHits) {
                bestHits = hits;
                bestCode = entry.getKey();
                bestReasons = reasons;
            }
        }
        if (bestCode == null) {
            return new CategoryGuess(null, null, 0d, List.of());
        }
        String name = bestCode;
        if (categories != null) {
            for (Category category : categories) {
                if (bestCode.equalsIgnoreCase(category.getCode())) {
                    name = category.getName();
                    break;
                }
            }
        }
        double confidence = Math.min(0.95, 0.45 + bestHits * 0.05);
        return new CategoryGuess(bestCode, name, confidence,
                List.of("命中意图词：" + String.join("、", bestReasons)));
    }

}
