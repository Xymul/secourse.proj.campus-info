package org.secourse.hzau.campus.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 搜索词库与停用词：为「自实现的轻量语义搜索」提供领域词典。
 *
 * <p>说明：按需求约定，本阶段不接入大模型，AI 智能搜索以「词典 + 统计打分」的
 * 规则算法实现，后续可平滑替换为真正的模型/向量检索。</p>
 */
public final class SearchLexicon {

    private SearchLexicon() {
    }

    /** 分类意图词典：关键词 -> 分类编码 */
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("LOST_FOUND", List.of("失物", "丢失", "丢了", "捡到", "拾到", "招领", "认领", "寻物", "寻找",
                "钱包", "钥匙", "校园卡", "身份证", "耳机", "雨伞", "书包", "水杯", "证件", "丢"));
        CATEGORY_KEYWORDS.put("SECONDHAND", List.of("二手", "出售", "转让", "求购", "闲置", "收一个", "入手", "出个", "便宜卖",
                "包邮", "价格", "多少钱", "收二手", "卖", "买的", "求购一个", "折价"));
        CATEGORY_KEYWORDS.put("TEAM", List.of("组队", "招人", "缺人", "搭子", "交友", "拼单", "拼车", "同行", "约球", "约饭",
                "一起", "队友", "社团", "招募队友", "找人", "结伴"));
        CATEGORY_KEYWORDS.put("ACTIVITY", List.of("活动", "讲座", "比赛", "报名", "演出", "宣讲", "招新", "运动会", "workshop",
                "沙龙", "论坛", "分享会", "培训", "开课", "竞赛"));
        CATEGORY_KEYWORDS.put("TALK", List.of("吐槽", "避雷", "安利", "评价", "说说", "分享", "心得", "体验", "推荐", "涨价",
                "好吃", "难吃", "排队", "吐槽一下"));
        CATEGORY_KEYWORDS.put("OTHER", List.of("求助", "咨询", "问一下", "求问", "有没有人知道", "谁知道", "怎么办", "急"));
    }

    /** 通用校园地点词（用于地点意图识别） */
    public static final List<String> PLACE_WORDS = List.of(
            "图书馆", "食堂", "宿舍", "操场", "体育馆", "教学楼", "实验楼", "校门口", "南门", "北门", "东门", "西门",
            "超市", "快递站", "快递", "咖啡厅", "自习室", "报告厅", "篮球场", "足球场", "游泳馆", "校医院", "打印店",
            "综合楼", "公寓", "湖畔", "广场", "菜鸟驿站");

    /** 时间意图词 -> 回溯天数（0 表示今天） */
    public static final Map<String, Integer> TIME_WORDS = new LinkedHashMap<>();

    static {
        TIME_WORDS.put("今天", 0);
        TIME_WORDS.put("今日", 0);
        TIME_WORDS.put("刚刚", 0);
        TIME_WORDS.put("昨天", 1);
        TIME_WORDS.put("这两天", 2);
        TIME_WORDS.put("最近", 3);
        TIME_WORDS.put("近期", 3);
        TIME_WORDS.put("这几天", 3);
        TIME_WORDS.put("本周", 7);
        TIME_WORDS.put("这周", 7);
        TIME_WORDS.put("周末", 7);
        TIME_WORDS.put("一周", 7);
        TIME_WORDS.put("本月", 30);
    }

    /** 就近/周边意图词 */
    public static final List<String> NEARBY_WORDS = List.of("附近", "周边", "旁边", "近距离", "最近的", "附近有");

    /** 停用词与疑问词：不参与关键词打分 */
    public static final Set<String> STOP_WORDS = Set.of(
            "我", "想", "要", "找", "找一下", "有没有", "谁", "哪里", "什么", "怎么", "请问", "一下", "的", "了", "吗",
            "呢", "求", "帮忙", "谢谢", "请问一下", "请问有", "有没有人", "知道", "请问哪里", "以及", "和", "或者",
            "是", "在", "有", "个", "把", "被", "能", "可以", "怎么走", "如何", "吗?", "啊", "吧", "哦", "嗯");

    /** 紧急 / 长期词，用于有效期建议 */
    public static final List<String> URGENT_WORDS = List.of("急", "限时", "今天", "明天", "马上", "尽快", "最后一", "秒杀");
    public static final List<String> LONG_TERM_WORDS = List.of("长期", "长期有效", "常驻", "持续", "常招", "每个月");

    /** 分类对应的常见标签补充词 */
    private static final Map<String, List<String>> CATEGORY_TAGS = Map.of(
            "LOST_FOUND", List.of("失物招领", "拾金不昧", "急寻"),
            "SECONDHAND", List.of("闲置", "低价", "可议价"),
            "TEAM", List.of("组队", "搭子", "招募"),
            "ACTIVITY", List.of("报名", "活动"),
            "TALK", List.of("校园日常", "分享"),
            "OTHER", List.of("校园求助")
    );

    public static Map<String, List<String>> categoryKeywords() {
        return CATEGORY_KEYWORDS;
    }

    public static List<String> categoryTags(String categoryCode) {
        return CATEGORY_TAGS.getOrDefault(categoryCode, List.of());
    }

    /** 敏感/风险词：发布阶段提示，后台治理参考 */
    public static final List<String> SENSITIVE_WORDS = List.of(
            "代考", "代写论文", "刷单", "赌博", "博彩", "贷款", "校园贷", "裸聊", "约炮", "枪支", "毒品", "传销",
            "办证", "假证", "刷信誉", "兼职刷单", "高利贷", "洗钱", "翻墙软件", "出售答案", "替考");
}
