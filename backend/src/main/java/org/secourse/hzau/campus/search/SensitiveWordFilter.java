package org.secourse.hzau.campus.search;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感 / 风险信息检测（自实现，规则词表）：
 * 发布阶段给出风险提示，后台治理阶段作为审核参考。
 */
public final class SensitiveWordFilter {

    private SensitiveWordFilter() {
    }

    /** 返回命中的敏感词列表 */
    public static List<String> detect(String text) {
        List<String> hits = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return hits;
        }
        String normalized = text.toLowerCase();
        for (String word : SearchLexicon.SENSITIVE_WORDS) {
            if (normalized.contains(word.toLowerCase())) {
                hits.add(word);
            }
        }
        return hits;
    }

    public static boolean containsSensitive(String text) {
        return !detect(text).isEmpty();
    }

    /** 风险等级：LOW / MEDIUM / HIGH */
    public static String riskLevel(String text) {
        int hits = detect(text).size();
        if (hits == 0) {
            return "LOW";
        }
        return hits == 1 ? "MEDIUM" : "HIGH";
    }

    public static String riskDescription(String text) {
        List<String> hits = detect(text);
        if (hits.isEmpty()) {
            return "未发现敏感词，内容看起来合规";
        }
        return "检测到可能的敏感信息：" + String.join("、", hits) + "，请确认内容是否符合平台规范";
    }
}
