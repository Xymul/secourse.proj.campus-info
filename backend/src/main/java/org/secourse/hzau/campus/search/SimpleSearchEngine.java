package org.secourse.hzau.campus.search;

import org.secourse.hzau.campus.entity.Post;
import org.secourse.hzau.campus.entity.enums.PostStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自实现的相关性检索引擎（不依赖 Elasticsearch / 大模型）：
 *
 * <ul>
 *   <li>打分：BM25 变体（字段加权 + 文档长度归一化 + IDF）；</li>
 *   <li>加权：意图匹配（分类/地点/时间/价格/距离）、信息新鲜度、协同验证可信度、互动热度；</li>
 *   <li>可解释：为每条结果输出「匹配原因」，为每次条件放宽输出降级说明；</li>
 *   <li>健壮性：条件逐级放宽（周边→时间→地点→分类→关键词），最终降级为热门推荐，保证不空手而归。</li>
 * </ul>
 */
public class SimpleSearchEngine {

    private static final double K1 = 1.2;
    private static final double B = 0.6;
    private static final double FIELD_TITLE = 3.0;
    private static final double FIELD_TAGS = 2.0;
    private static final double FIELD_LOCATION = 1.6;
    private static final double FIELD_CATEGORY = 1.5;
    private static final double FIELD_CONTENT = 1.0;

    private static final double BOOST_CATEGORY = 3.0;
    private static final double BOOST_LOCATION = 2.4;
    private static final double BOOST_TIME = 1.4;
    private static final double BOOST_PRICE = 1.0;
    private static final double BOOST_NEARBY = 2.0;
    private static final double BOOST_FRESH_MAX = 1.5;
    private static final double BOOST_EXACT_PHRASE = 3.5;

    /** 检索上下文 */
    public record Context(double freshnessHalfLifeDays, double radiusKm, Double userLat, Double userLng) {
    }

    /** 检索结果与降级信息 */
    public record Result(List<SearchHit> hits, List<String> appliedConditions, List<String> relaxedConditions,
                         boolean fallback) {
    }

    /** 文档画像：各字段的词频表 */
    private record DocProfile(Map<String, Integer> title, Map<String, Integer> tags, Map<String, Integer> location,
                              Map<String, Integer> category, Map<String, Integer> content, int length) {
    }

    /**
     * 执行检索：意图条件逐级放宽，直到有结果为止。
     *
     * @param candidates 候选集（已按学校 + 有效状态过滤）
     */
    public Result search(List<Post> candidates, SearchIntent intent, Context context) {
        List<String> applied = new ArrayList<>();
        List<String> relaxed = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            return new Result(List.of(), applied, relaxed, true);
        }

        Set<String> active = new LinkedHashSet<>();
        if (intent.getKeywords() != null && !intent.getKeywords().isEmpty()) {
            active.add("keyword");
            applied.add("关键词：" + String.join("、", intent.getKeywords()));
        }
        if (intent.getCategoryCode() != null) {
            active.add("category");
            applied.add("分类：" + (intent.getCategoryName() == null ? intent.getCategoryCode() : intent.getCategoryName()));
        }
        if (intent.getLocationId() != null) {
            active.add("location");
            applied.add("地点：" + intent.getLocationName());
        }
        if (intent.getCreatedAfter() != null) {
            active.add("time");
            applied.add("时间：" + intent.getTimeLabel());
        }
        if (intent.isNearby() && context.userLat() != null && context.userLng() != null) {
            active.add("nearby");
            applied.add("距离：" + context.radiusKm() + " 公里内");
        }

        List<SearchHit> hits = rank(candidates, intent, context, active);
        if (!hits.isEmpty()) {
            return new Result(hits, applied, relaxed, false);
        }

        // 条件逐级放宽：先去掉最苛刻的空间/时间条件，最后退化为纯关键词
        String[][] relaxOrder = {
                {"nearby", "已放宽距离范围"},
                {"time", "已放宽时间范围"},
                {"location", "已放宽地点限制"},
                {"category", "已放宽分类限制"},
                {"keyword", "已放宽关键词严格匹配"}
        };
        for (String[] step : relaxOrder) {
            if (!active.remove(step[0])) {
                continue;
            }
            applied.removeIf(label -> matchesConditionLabel(step[0], label));
            relaxed.add(step[1]);
            hits = rank(candidates, intent, context, active);
            if (!hits.isEmpty()) {
                return new Result(hits, applied, relaxed, false);
            }
        }
        return new Result(List.of(), applied, relaxed, true);
    }

    private boolean matchesConditionLabel(String key, String label) {
        return switch (key) {
            case "nearby" -> label.startsWith("距离：");
            case "time" -> label.startsWith("时间：");
            case "location" -> label.startsWith("地点：");
            case "category" -> label.startsWith("分类：");
            default -> label.startsWith("关键词：");
        };
    }

    /** 纯相关度排序（不应用硬条件），用于「热门推荐」等场景 */
    public List<SearchHit> rankOnly(List<Post> candidates, SearchIntent intent, Context context) {
        return rank(candidates, intent, context, Set.of());
    }

    private List<SearchHit> rank(List<Post> candidates, SearchIntent intent, Context context, Set<String> active) {
        Map<Long, Double> distances = new java.util.HashMap<>();
        List<Post> pool = new ArrayList<>();
        for (Post post : candidates) {
            Double distance = distanceTo(post, context);
            if (distance != null) {
                distances.put(post.getPostId(), distance);
            }
            if (passesFilters(post, intent, context, active, distance)) {
                pool.add(post);
            }
        }
        if (pool.isEmpty()) {
            return List.of();
        }

        Set<String> dictionary = dictionaryOf(pool);
        Map<String, Integer> queryTerms = new java.util.LinkedHashMap<>();
        for (ChineseTokenizer.Token token : ChineseTokenizer.tokenize(intent.getRawQuery(), dictionary)) {
            queryTerms.merge(token.text(), token.weight(), Math::max);
        }
        if (intent.getKeywords() != null) {
            for (String keyword : intent.getKeywords()) {
                queryTerms.merge(keyword, ChineseTokenizer.WEIGHT_LEXICON, Math::max);
            }
        }

        List<DocProfile> profiles = new ArrayList<>(pool.size());
        long totalLength = 0L;
        Map<String, Integer> docFreq = new java.util.HashMap<>();
        for (Post post : pool) {
            DocProfile profile = buildProfile(post, dictionary);
            profiles.add(profile);
            totalLength += profile.length();
            for (String term : queryTerms.keySet()) {
                if (termFrequency(profile, term) > 0) {
                    docFreq.merge(term, 1, Integer::sum);
                }
            }
        }
        double avgLength = Math.max(1d, (double) totalLength / pool.size());
        int docCount = pool.size();

        List<SearchHit> hits = new ArrayList<>(pool.size());
        for (int i = 0; i < pool.size(); i++) {
            Post post = pool.get(i);
            DocProfile profile = profiles.get(i);
            SearchHit hit = new SearchHit(post);
            hit.setDistanceKm(distances.get(post.getPostId()));
            double score = 0d;

            for (Map.Entry<String, Integer> term : queryTerms.entrySet()) {
                double tf = termFrequency(profile, term.getKey());
                if (tf <= 0) {
                    continue;
                }
                int df = Math.max(1, docFreq.getOrDefault(term.getKey(), 1));
                double idf = Math.log(1 + (docCount - df + 0.5) / (df + 0.5));
                double termWeight = term.getValue() / (double) ChineseTokenizer.WEIGHT_LEXICON;
                score += idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * profile.length() / avgLength)) * termWeight;
                appendTermReason(hit, profile, term.getKey());
            }

            score += intentBoosts(post, intent, context, hit, distances.get(post.getPostId()));
            score += qualityBoost(post, context, hit);
            hit.setScore(score);
            hits.add(hit);
        }

        hits.sort(Comparator.comparingDouble(SearchHit::getScore).reversed());
        normalizeRelevance(hits);
        return hits;
    }

    private boolean passesFilters(Post post, SearchIntent intent, Context context, Set<String> active, Double distance) {
        if (active.contains("category") && intent.getCategoryCode() != null) {
            if (post.getCategory() == null || !intent.getCategoryCode().equalsIgnoreCase(post.getCategory().getCode())) {
                return false;
            }
        }
        if (active.contains("location") && intent.getLocationId() != null) {
            if (post.getLocation() == null || !intent.getLocationId().equals(post.getLocation().getLocationId())) {
                return false;
            }
        }
        if (active.contains("time") && intent.getCreatedAfter() != null) {
            if (post.getCreatedAt() == null || post.getCreatedAt().isBefore(intent.getCreatedAfter())) {
                return false;
            }
        }
        if (active.contains("nearby")) {
            if (distance == null || distance > context.radiusKm()) {
                return false;
            }
        }
        if (active.contains("keyword") && intent.getKeywords() != null && !intent.getKeywords().isEmpty()) {
            String haystack = haystack(post);
            boolean matched = false;
            for (String keyword : intent.getKeywords()) {
                if (haystack.contains(keyword.toLowerCase())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private Set<String> dictionaryOf(List<Post> pool) {
        Set<String> dictionary = new LinkedHashSet<>(SearchLexicon.PLACE_WORDS);
        SearchLexicon.categoryKeywords().values().forEach(dictionary::addAll);
        for (Post post : pool) {
            if (post.getCategory() != null) {
                dictionary.add(post.getCategory().getName());
            }
            if (post.getLocation() != null) {
                dictionary.add(post.getLocation().getName());
            }
        }
        dictionary.removeIf(word -> word == null || word.length() < 2);
        return dictionary;
    }

    private String haystack(Post post) {
        StringBuilder sb = new StringBuilder();
        sb.append(post.getTitle() == null ? "" : post.getTitle()).append(' ')
                .append(post.getContent() == null ? "" : post.getContent()).append(' ')
                .append(post.getTags() == null ? "" : post.getTags()).append(' ');
        if (post.getCategory() != null && post.getCategory().getName() != null) {
            sb.append(post.getCategory().getName()).append(' ');
        }
        if (post.getLocation() != null && post.getLocation().getName() != null) {
            sb.append(post.getLocation().getName());
        }
        return sb.toString().toLowerCase();
    }

    private Double distanceTo(Post post, Context context) {
        if (context.userLat() == null || context.userLng() == null
                || post.getLatitude() == null || post.getLongitude() == null) {
            return null;
        }
        return TextUtils.haversineKm(context.userLat(), context.userLng(), post.getLatitude(), post.getLongitude());
    }

    // ------------------------------------------------------------------ 打分细节

    private DocProfile buildProfile(Post post, Set<String> dictionary) {
        Map<String, Integer> title = frequencies(post.getTitle(), dictionary);
        Map<String, Integer> tags = frequencies(post.getTags(), dictionary);
        Map<String, Integer> content = frequencies(post.getContent(), dictionary);
        Map<String, Integer> location = frequencies(post.getLocation() == null ? null : post.getLocation().getName(), dictionary);
        Map<String, Integer> category = frequencies(post.getCategory() == null ? null : post.getCategory().getName(), dictionary);
        int length = sum(title) + sum(tags) + sum(content) + sum(location) + sum(category);
        return new DocProfile(title, tags, location, category, content, Math.max(1, length));
    }

    private Map<String, Integer> frequencies(String text, Set<String> dictionary) {
        Map<String, Integer> map = new java.util.HashMap<>();
        for (ChineseTokenizer.Token token : ChineseTokenizer.tokenize(text, dictionary)) {
            map.merge(token.text(), 1, Integer::sum);
        }
        return map;
    }

    private int sum(Map<String, Integer> map) {
        return map.values().stream().mapToInt(Integer::intValue).sum();
    }

    private double termFrequency(DocProfile profile, String term) {
        return FIELD_TITLE * profile.title().getOrDefault(term, 0)
                + FIELD_TAGS * profile.tags().getOrDefault(term, 0)
                + FIELD_LOCATION * profile.location().getOrDefault(term, 0)
                + FIELD_CATEGORY * profile.category().getOrDefault(term, 0)
                + FIELD_CONTENT * profile.content().getOrDefault(term, 0);
    }

    private void appendTermReason(SearchHit hit, DocProfile profile, String term) {
        if (profile.title().getOrDefault(term, 0) > 0) {
            hit.addReason("标题包含「" + term + "」");
        } else if (profile.tags().getOrDefault(term, 0) > 0) {
            hit.addReason("标签命中「" + term + "」");
        } else if (profile.content().getOrDefault(term, 0) > 0) {
            hit.addReason("正文提到「" + term + "」");
        } else if (profile.location().getOrDefault(term, 0) > 0) {
            hit.addReason("地点相关「" + term + "」");
        }
    }

    /** 意图加成：把「理解到的条件」转化为可解释的排序权重 */
    private double intentBoosts(Post post, SearchIntent intent, Context context, SearchHit hit, Double distance) {
        double score = 0d;
        if (intent.getCategoryCode() != null && post.getCategory() != null
                && intent.getCategoryCode().equalsIgnoreCase(post.getCategory().getCode())) {
            score += BOOST_CATEGORY;
            hit.addReason("分类匹配：" + post.getCategory().getName());
        }
        if (intent.getLocationId() != null && post.getLocation() != null
                && intent.getLocationId().equals(post.getLocation().getLocationId())) {
            score += BOOST_LOCATION;
            hit.addReason("地点匹配：" + post.getLocation().getName());
        } else if (intent.getLocationName() != null && post.getLocation() != null
                && post.getLocation().getName().contains(intent.getLocationName())) {
            score += BOOST_LOCATION * 0.7;
            hit.addReason("地点相关：" + post.getLocation().getName());
        }
        if (intent.getCreatedAfter() != null && post.getCreatedAt() != null
                && !post.getCreatedAt().isBefore(intent.getCreatedAfter())) {
            score += BOOST_TIME;
            hit.addReason("发布时间：" + intent.getTimeLabel());
        }
        String raw = intent.getRawQuery();
        if (raw != null && !raw.isBlank() && post.getTitle() != null
                && post.getTitle().toLowerCase().contains(raw.toLowerCase())) {
            score += BOOST_EXACT_PHRASE;
            hit.addReason("标题完整包含您的描述");
        }
        if (intent.getMaxPrice() != null) {
            Double price = TextUtils.numberBeforeUnit(post.getContent() == null ? "" : post.getContent(), "元");
            if (price == null) {
                price = TextUtils.numberBeforeUnit(post.getTitle() == null ? "" : post.getTitle(), "元");
            }
            if (price != null) {
                if (price <= intent.getMaxPrice() * 1.15) {
                    score += BOOST_PRICE;
                    hit.addReason("价格符合预算（约 " + price.intValue() + " 元）");
                } else {
                    score -= BOOST_PRICE;
                }
            }
        }
        if (intent.isNearby() && distance != null) {
            double ratio = Math.max(0d, 1 - distance / Math.max(0.1, context.radiusKm()));
            score += BOOST_NEARBY * ratio;
            hit.addReason(distance < 1
                    ? "距您约 " + (int) Math.round(distance * 1000) + " 米"
                    : String.format("距您约 %.1f 公里", distance));
        }
        return score;
    }

    /** 质量加成：新鲜度 + 协同验证可信度 + 互动热度 */
    private double qualityBoost(Post post, Context context, SearchHit hit) {
        double score = freshnessBoost(post, context.freshnessHalfLifeDays());
        int trueCount = post.getVerifyTrueCount() == null ? 0 : post.getVerifyTrueCount();
        int falseCount = post.getVerifyFalseCount() == null ? 0 : post.getVerifyFalseCount();
        if (trueCount + falseCount > 0) {
            double ratio = (double) trueCount / (trueCount + falseCount);
            if (ratio >= 0.5) {
                score += 1.2 * ratio;
                if (trueCount >= 2) {
                    hit.addReason(trueCount + " 位同学证实（可信度 " + (int) Math.round(ratio * 100) + "%）");
                }
            } else {
                score -= 1.5 * (1 - ratio);
                if (falseCount >= 2) {
                    hit.addReason(falseCount + " 条证伪反馈，请注意甄别");
                }
            }
        }
        score += Math.min(1.0, post.getViewCount() / 200d) * 0.5
                + Math.min(1.0, post.getCommentCount() / 20d) * 0.6
                + Math.min(1.0, post.getLikeCount() / 20d) * 0.5;
        if (post.getStatus() == PostStatus.RESOLVED) {
            score -= 0.8;
        }
        return score;
    }

    private void normalizeRelevance(List<SearchHit> hits) {
        double max = hits.stream().mapToDouble(SearchHit::getScore).max().orElse(0d);
        for (SearchHit hit : hits) {
            double ratio = max <= 0 ? 0d : Math.max(0d, Math.min(1d, hit.getScore() / max));
            hit.setRelevance((int) Math.round(ratio * 100));
        }
    }

    /** 降级推荐：按热度返回热门信息 */
    public List<SearchHit> hotList(List<Post> candidates, int limit) {
        List<SearchHit> hits = new ArrayList<>();
        if (candidates == null) {
            return hits;
        }
        for (Post post : candidates) {
            SearchHit hit = new SearchHit(post);
            hit.setScore(hotScore(post));
            hit.addReason("平台热门信息推荐");
            hits.add(hit);
        }
        hits.sort(Comparator.comparingDouble(SearchHit::getScore).reversed());
        List<SearchHit> limited = new ArrayList<>(hits.stream().limit(limit).toList());
        normalizeRelevance(limited);
        return limited;
    }

    private double hotScore(Post post) {
        double score = Math.min(1.0, post.getViewCount() / 200d) * 1.5
                + Math.min(1.0, post.getCommentCount() / 20d) * 1.5
                + Math.min(1.0, post.getLikeCount() / 20d) * 1.2;
        return score + freshnessBoost(post, 7d);
    }

    private double freshnessBoost(Post post, double halfLifeDays) {
        if (post.getCreatedAt() == null) {
            return 0d;
        }
        long hours = Math.max(0, Duration.between(post.getCreatedAt(), LocalDateTime.now()).toHours());
        double ageDays = hours / 24d;
        return BOOST_FRESH_MAX * Math.pow(0.5, ageDays / Math.max(0.5, halfLifeDays));
    }



}
