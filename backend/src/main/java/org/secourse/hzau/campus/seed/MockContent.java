package org.secourse.hzau.campus.seed;

import java.util.List;
import java.util.Map;

/**
 * 模拟数据模板：用于生成 1500 余条校园信息（含江大、复旦、浙大三个校区）。
 */
public final class MockContent {

    private MockContent() {
    }

    /** 分类 -> 标题模板（{place} 会被替换为具体地点，{item} 会被替换为物品/主题） */
    public static final Map<String, List<String>> TITLES = Map.of(
            "LOST_FOUND", List.of(
                    "在{place}捡到一个{item}，失主速联系",
                    "丢失{item}！在{place}附近丢的",
                    "{place}拾到{item}，请失主认领",
                    "急寻{item}，最后一次见到是在{place}"),
            "SECONDHAND", List.of(
                    "出闲置{item}，{price}元可小刀",
                    "{item}转让，{place}自提",
                    "二手{item}低价出，成色不错",
                    "毕业清仓：{item}，{price}元带走"),
            "TEAM", List.of(
                    "求组队：{item}，{place}集合",
                    "{place}附近找{item}搭子",
                    "招募{item}队友，人数还差两个",
                    "有人一起{item}吗？{place}见"),
            "ACTIVITY", List.of(
                    "{place}举办{item}，欢迎报名",
                    "{item}活动通知（{place}）",
                    "本周{item}开始报名啦",
                    "{place}{item}志愿者招募"),
            "TALK", List.of(
                    "{place}的{item}体验分享",
                    "吐槽一下{place}的{item}",
                    "安利{place}的{item}，真的香",
                    "{place}{item}实测感受"),
            "OTHER", List.of(
                    "请问{place}的{item}怎么走",
                    "求助：关于{item}的问题",
                    "{item}相关咨询，{place}",
                    "有同学了解{place}的{item}吗")
    );

    /** 分类 -> 物品/主题词 */
    public static final Map<String, List<String>> ITEMS = Map.of(
            "LOST_FOUND", List.of("校园卡", "蓝牙耳机", "宿舍钥匙", "雨伞", "身份证", "水杯", "充电宝", "学生证",
                    "自行车钥匙", "笔记本电脑包", "实验报告", "保温杯"),
            "SECONDHAND", List.of("山地自行车", "折叠书桌", "台灯", "考研专业课教材", "机械键盘", "显示器",
                    "羽毛球拍", "电吹风", "小冰箱", "行李箱", "四六级真题", "路由器"),
            "TEAM", List.of("考研自习", "夜跑", "篮球三对三", "羽毛球", "英语口语练习", "编程竞赛",
                    "周末爬惠山", "电影观影", "汉服拍摄", "图书馆占座学习"),
            "ACTIVITY", List.of("就业宣讲会", "校园歌手大赛", "人工智能讲座", "编程马拉松", "社团招新",
                    "运动会志愿者", "无偿献血", "创新创业分享会", "摄影展", "读书会"),
            "TALK", List.of("食堂新窗口", "快递驿站取件效率", "图书馆自习区空调", "校园夜景", "校车时刻",
                    "宿舍热水供应", "校园网速度", "便利店物价"),
            "OTHER", List.of("打印店位置", "校医院挂号", "学生证补办", "教务系统选课", "体育场开放时间",
                    "宿舍报修流程", "校园卡充值")
    );

    /** 正文模板 */
    public static final List<String> CONTENTS = List.of(
            "具体情况：{item}，位置在{place}。信息真实，欢迎需要的同学联系我，也请同学们帮忙转发。",
            "详细说明：{item}目前在{place}，时间比较急，如果有同学知道相关线索麻烦留言，谢谢！",
            "补充说明：{item}（{place}）。已经和几位同学确认过，信息有效，看到请及时联系。",
            "说明一下：关于{item}，位置在{place}，我会在这两天持续关注回复，有问题可以直接评论。",
            "内容更新：{item}，地点{place}，价格和细节可以商量，也可以先到现场看看再决定。");

    /** 标签池（与分类无关的通用标签） */
    public static final List<String> COMMON_TAGS = List.of("校园生活", "互助", "急", "推荐", "新生必看",
            "新学期", "免费", "可议价", "限时", "周末");

    public static final List<String> COMMENT_TEXTS = List.of(
            "这个信息很有用，谢谢分享！", "我昨天路过看到了，情况属实。", "请问现在还在吗？",
            "已经联系上啦，感谢楼主。", "建议顺路去{place}看看，应该还在。", "帮顶，希望尽快解决～",
            "这也太及时了，正好需要。", "我朋友说见过，晚点帮你问问。");

    public static final List<String> REPORT_REASONS = List.of("信息已过期", "内容与实际不符", "涉嫌广告",
            "包含不文明用语", "疑似诈骗信息", "重复发布");
}
