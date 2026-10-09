package org.secourse.hzau.campus.search;

import org.secourse.hzau.campus.entity.CampusLocation;
import org.secourse.hzau.campus.entity.Category;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 「AI 辅助发布」建议器（自实现规则算法）：
 *
 * <p>依据标题与正文给出分类、标签、有效期建议，并给出敏感信息与隐私提示。
 * 用户可以在前端修改全部建议值，保证「降低发布门槛但不替用户做决定」。</p>
 */
public final class AiPublishAdvisor {

    private AiPublishAdvisor() {
    }

    public static AiPublishAdvice advise(String title,
                                         String content,
                                         Collection<Category> categories,
                                         Collection<CampusLocation> locations,
                                         String selectedCategoryCode) {
        String text = ((title == null ? "" : title) + " " + (content == null ? "" : content)).trim();
        AiPublishAdvice advice = new AiPublishAdvice();

        Category fallback = firstOf(categories, selectedCategoryCode);
        SearchIntentParser.CategoryGuess guess = SearchIntentParser.classifyCategory(text, categories);
        Category category = findCategory(categories, guess.code());
        if (category == null) {
            category = fallback;
        }
        if (category != null) {
            advice.setCategoryCode(category.getCode());
            advice.setCategoryName(category.getName());
        }
        if (guess.code() != null && category != null && guess.code().equalsIgnoreCase(category.getCode())) {
            advice.setCategoryConfidence(guess.confidence());
            advice.setCategoryReason(guess.reasons().isEmpty() ? "依据正文关键词推测" : String.join("；", guess.reasons()));
        } else if (category != null) {
            advice.setCategoryConfidence(0.35);
            advice.setCategoryReason("未识别到明显的分类意图词，已默认归类为「" + category.getName() + "」，可手动调整");
        }

        Set<String> dictionary = SearchIntentParser.buildDictionary(categories, locations);
        Set<String> tags = new LinkedHashSet<>();
        if (category != null) {
            tags.addAll(SearchLexicon.categoryTags(category.getCode()));
        }
        tags.addAll(ChineseTokenizer.extractKeywords(text, dictionary, 6));
        String locationName = locations == null ? null
                : TextUtils.firstMatched(text, locations.stream().map(CampusLocation::getName).toList());
        if (locationName != null) {
            tags.add(locationName);
        }
        advice.setTags(tags.stream().filter(t -> t != null && t.length() >= 2).limit(5).toList());

        int baseDays = category == null || category.getBaseValidDays() == null ? 7 : category.getBaseValidDays();
        if (TextUtils.containsAny(text, SearchLexicon.URGENT_WORDS)) {
            advice.setSuggestedValidDays(Math.max(1, Math.min(baseDays, 3)));
            advice.setValidDaysReason("内容包含「急/今天/限时」等时效性词汇，建议 3 天内有效");
        } else if (TextUtils.containsAny(text, SearchLexicon.LONG_TERM_WORDS)) {
            advice.setSuggestedValidDays(Math.max(baseDays, 30));
            advice.setValidDaysReason("内容属于长期有效信息，建议 30 天有效期");
        } else {
            advice.setSuggestedValidDays(baseDays);
            advice.setValidDaysReason("采用分类「" + (category == null ? "默认" : category.getName())
                    + "」的默认有效期 " + baseDays + " 天");
        }

        List<String> riskWords = SensitiveWordFilter.detect(text);
        advice.setRiskWords(riskWords);
        advice.setRiskLevel(SensitiveWordFilter.riskLevel(text));
        advice.setContactDetected(TextUtils.looksLikeContact(text));

        List<String> tips = new ArrayList<>();
        tips.add("分类建议：" + (advice.getCategoryName() == null ? "未识别" : advice.getCategoryName())
                + "（可手动修改）");
        if (!riskWords.isEmpty()) {
            tips.add("内容合规提示：" + SensitiveWordFilter.riskDescription(text));
        }
        if (advice.isContactDetected()) {
            tips.add("检测到联系方式，建议填写到「联系方式」字段，页面将按权限脱敏展示");
        }
        if (locationName == null) {
            tips.add("未能从正文识别地点，建议在地图上选择具体位置，便于同学按位置查找");
        }
        tips.add(advice.getValidDaysReason());
        advice.setTips(tips);

        advice.setSummary(String.format("已根据标题与正文生成建议：分类「%s」，有效期 %d 天，标签 %s；全部建议均可修改。",
                advice.getCategoryName() == null ? "其他" : advice.getCategoryName(),
                advice.getSuggestedValidDays(),
                advice.getTags().isEmpty() ? "（暂无）" : String.join("、", advice.getTags())));
        return advice;
    }

    private static Category findCategory(Collection<Category> categories, String code) {
        if (categories == null || code == null) {
            return null;
        }
        return categories.stream().filter(c -> code.equalsIgnoreCase(c.getCode())).findFirst().orElse(null);
    }

    private static Category firstOf(Collection<Category> categories, String selectedCategoryCode) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        Category selected = findCategory(categories, selectedCategoryCode);
        if (selected != null) {
            return selected;
        }
        return categories.stream()
                .filter(c -> "OTHER".equalsIgnoreCase(c.getCode()))
                .findFirst()
                .orElse(categories.iterator().next());
    }
}
