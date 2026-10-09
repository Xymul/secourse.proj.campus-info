package org.secourse.hzau.campus.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.secourse.hzau.campus.entity.Category;
import org.secourse.hzau.campus.entity.Post;
import org.secourse.hzau.campus.entity.enums.PostStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自实现搜索引擎的单元测试：分词、意图解析、相关性排序与条件放宽。
 */
class SimpleSearchEngineTest {

    private Category category(String code, String name, int validDays) {
        Category category = new Category();
        category.setCode(code);
        category.setName(name);
        category.setBaseValidDays(validDays);
        return category;
    }

    private Post post(String title, String content, String tags, Category category) {
        Post post = new Post();
        post.setPostId((long) (title.hashCode() & 0x7fffffff));
        post.setTitle(title);
        post.setContent(content);
        post.setTags(tags);
        post.setCategory(category);
        post.setStatus(PostStatus.PUBLISHED);
        post.setVerifyTrueCount(2);
        post.setVerifyFalseCount(0);
        post.setViewCount(30);
        post.setCommentCount(2);
        post.setLikeCount(3);
        post.setHotScore(1.0);
        post.setExpireAt(LocalDateTime.now().plusDays(3));
        post.setCreatedAt(LocalDateTime.now().minusHours(3));
        return post;
    }

    @Test
    @DisplayName("中文分词：词典词优先，命中领域词与关键词")
    void shouldTokenizeChineseWithLexicon() {
        Set<String> dictionary = SearchIntentParser.buildDictionary(
                List.of(category("LOST_FOUND", "失物招领", 30), category("SECONDHAND", "二手交易", 14)), List.of());

        List<String> words = ChineseTokenizer.tokenize("在图书馆捡到一个钥匙", dictionary).stream()
                .map(ChineseTokenizer.Token::text).toList();

        assertThat(words).contains("图书馆", "钥匙");
    }

    @Test
    @DisplayName("分词：停用词与疑问词不应参与匹配")
    void shouldDropStopWords() {
        Set<String> dictionary = SearchIntentParser.buildDictionary(List.of(), List.of());
        List<String> words = ChineseTokenizer.tokenize("请问有没有人可以帮我看一下", dictionary).stream()
                .map(ChineseTokenizer.Token::text).toList();

        assertThat(words).doesNotContain("请问", "有没有", "可以", "一下");
    }

    @Test
    @DisplayName("意图解析：识别分类、地点与时间条件")
    void shouldParseIntent() {
        List<Category> categories = List.of(category("LOST_FOUND", "失物招领", 30),
                category("SECONDHAND", "二手交易", 14), category("ACTIVITY", "校园活动", 7));

        SearchIntent intent = SearchIntentParser.parse("今天图书馆有失物招领吗", categories, List.of(), 3.0);

        assertThat(intent.getCategoryCode()).isEqualTo("LOST_FOUND");
        assertThat(intent.getLocationName()).isEqualTo("图书馆");
        assertThat(intent.getCreatedAfter()).isNotNull();
        assertThat(intent.isUnderstood()).isTrue();
        assertThat(intent.getKeywords()).isNotEmpty();
    }

    @Test
    @DisplayName("意图解析：识别距离与预算条件")
    void shouldParseNearbyAndPrice() {
        List<Category> categories = List.of(category("SECONDHAND", "二手交易", 14));

        SearchIntent intent = SearchIntentParser.parse("附近3公里内预算300元的二手台灯", categories, List.of(), 5.0);

        assertThat(intent.isNearby()).isTrue();
        assertThat(intent.getRadiusKm()).isEqualTo(3.0);
        assertThat(intent.getMaxPrice()).isEqualTo(300d);
        assertThat(intent.getCategoryCode()).isEqualTo("SECONDHAND");
    }

    @Test
    @DisplayName("相关性排序：命中标题与分类的信息排在前面，并给出匹配原因")
    void shouldRankRelevantPostFirst() {
        Category lostFound = category("LOST_FOUND", "失物招领", 30);
        Category secondHand = category("SECONDHAND", "二手交易", 14);
        List<Category> categories = List.of(lostFound, secondHand);

        Post keyPost = post("在图书馆丢了一把钥匙，急寻",
                "校园卡和钥匙一起丢的，位置在图书馆二楼", "失物招领,图书馆,钥匙", lostFound);
        Post otherPost = post("出闲置台灯", "台灯九成新，价格可议", "二手交易,台灯", secondHand);

        SearchIntent intent = SearchIntentParser.parse("图书馆丢的钥匙", categories, List.of(), 3.0);
        SimpleSearchEngine engine = new SimpleSearchEngine();
        SimpleSearchEngine.Result result = engine.search(List.of(otherPost, keyPost), intent,
                new SimpleSearchEngine.Context(7.0, 3.0, null, null));

        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits().get(0).getPost().getTitle()).contains("钥匙");
        assertThat(result.hits().get(0).getRelevance()).isEqualTo(100);
        assertThat(result.hits().get(0).getReasons()).isNotEmpty();
    }

    @Test
    @DisplayName("条件放宽：关键词无命中时应自动放宽并给出说明，而不是直接返回空结果")
    void shouldRelaxConditionsWhenNothingMatches() {
        Category activity = category("ACTIVITY", "校园活动", 7);
        Category talk = category("TALK", "分享吐槽", 7);

        Post post = post("食堂新窗口体验", "三食堂新窗口味道不错", "分享吐槽,食堂", talk);
        SearchIntent intent = SearchIntentParser.parse("量子计算机讲座", List.of(activity, talk), List.of(), 3.0);

        SimpleSearchEngine engine = new SimpleSearchEngine();
        SimpleSearchEngine.Result result = engine.search(List.of(post), intent,
                new SimpleSearchEngine.Context(7.0, 3.0, null, null));

        assertThat(result.hits()).isNotEmpty();
        assertThat(result.relaxedConditions()).isNotEmpty();
    }

    @Test
    @DisplayName("AI 辅助发布：给出分类、标签与有效期建议")
    void shouldAdvisePublishMetadata() {
        List<Category> categories = List.of(category("LOST_FOUND", "失物招领", 30),
                category("SECONDHAND", "二手交易", 14), category("OTHER", "其他", 7));

        AiPublishAdvice advice = AiPublishAdvisor.advise(
                "急！在图书馆捡到校园卡一张",
                "今天下午在图书馆三楼自习区捡到一张校园卡，请失主尽快联系我，急！",
                categories, List.of(), null);

        assertThat(advice.getCategoryCode()).isEqualTo("LOST_FOUND");
        assertThat(advice.getTags()).isNotEmpty();
        assertThat(advice.getSuggestedValidDays()).isLessThanOrEqualTo(30);
        assertThat(advice.getSummary()).isNotBlank();
        assertThat(advice.getTips()).isNotEmpty();
    }

    @Test
    @DisplayName("敏感词检测：命中风险词并给出等级")
    void shouldDetectSensitiveWords() {
        assertThat(SensitiveWordFilter.detect("代考包过，需要的同学私聊")).contains("代考");
        assertThat(SensitiveWordFilter.riskLevel("代考包过，需要的同学私聊")).isNotEqualTo("LOW");
        assertThat(SensitiveWordFilter.riskLevel("图书馆有人自习吗")).isEqualTo("LOW");
    }

    @Test
    @DisplayName("距离计算：Haversine 结果与预期量级一致")
    void shouldComputeHaversineDistance() {
        // 江南大学图书馆附近 ~ 200 米
        double distance = TextUtils.haversineKm(31.4856, 120.2653, 31.4874, 120.2653);
        assertThat(distance).isBetween(0.1, 0.3);
    }

}
