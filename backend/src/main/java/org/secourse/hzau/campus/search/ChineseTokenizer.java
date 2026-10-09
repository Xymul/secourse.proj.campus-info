package org.secourse.hzau.campus.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 轻量中文分词器（自实现）：
 *
 * <ol>
 *   <li>英文/数字：按连续字母数字切分为整词；</li>
 *   <li>中文：以「领域词典最长匹配 + 二元切分(bigram) + 单字」三种粒度产出候选词，
 *       并给出粒度权重（词典 = 3，bigram = 2，单字 = 1）；</li>
 *   <li>过滤停用词，保证查询与文档使用同一套切分规则，从而可以直接做词项匹配统计。</li>
 * </ol>
 */
public final class ChineseTokenizer {

    public static final int WEIGHT_LEXICON = 3;
    public static final int WEIGHT_BIGRAM = 2;
    public static final int WEIGHT_CHAR = 1;

    /** 分词结果：词项 + 粒度权重 */
    public record Token(String text, int weight) {
    }

    private ChineseTokenizer() {
    }

    /** 将文本切分为带权重的词项列表（可能包含重复项，用于词频统计） */
    public static List<Token> tokenize(String text, Collection<String> dictionary) {
        List<Token> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String normalized = TextUtils.normalize(text);
        int i = 0;
        while (i < normalized.length()) {
            char c = normalized.charAt(i);
            if (TextUtils.isAsciiWord(c)) {
                int start = i;
                while (i < normalized.length() && TextUtils.isAsciiWord(normalized.charAt(i))) {
                    i++;
                }
                String word = normalized.substring(start, i);
                if (!SearchLexicon.STOP_WORDS.contains(word)) {
                    tokens.add(new Token(word, WEIGHT_LEXICON));
                }
            } else if (TextUtils.isCjk(c)) {
                int start = i;
                while (i < normalized.length() && TextUtils.isCjk(normalized.charAt(i))) {
                    i++;
                }
                String run = normalized.substring(start, i);
                tokens.addAll(tokenizeCjkRun(run, dictionary));
            } else {
                i++;
            }
        }
        return tokens;
    }

    private static List<Token> tokenizeCjkRun(String run, Collection<String> dictionary) {
        List<Token> tokens = new ArrayList<>();
        boolean[] matched = new boolean[run.length()];
        // 1) 词典最长匹配（贪心，优先级高于 bigram）
        for (int start = 0; start < run.length(); start++) {
            if (matched[start]) {
                continue;
            }
            String best = null;
            for (String word : dictionary) {
                int len = word.length();
                if (len < 2 || start + len > run.length()) {
                    continue;
                }
                if (run.startsWith(word, start) && (best == null || len > best.length())) {
                    best = word;
                }
            }
            if (best != null) {
                for (int k = start; k < start + best.length(); k++) {
                    matched[k] = true;
                }
                tokens.add(new Token(best, WEIGHT_LEXICON));
            }
        }
        // 2) 未被词典覆盖的部分做 bigram 切分
        boolean[] bigramUsed = new boolean[run.length()];
        for (int start = 0; start + 1 < run.length(); start++) {
            if (matched[start] || matched[start + 1]) {
                continue;
            }
            String bigram = run.substring(start, start + 2);
            if (SearchLexicon.STOP_WORDS.contains(bigram)) {
                continue;
            }
            bigramUsed[start] = true;
            bigramUsed[start + 1] = true;
            tokens.add(new Token(bigram, WEIGHT_BIGRAM));
        }
        // 3) 仍然孤立的中文字符按单字处理
        for (int idx = 0; idx < run.length(); idx++) {
            if (matched[idx] || bigramUsed[idx]) {
                continue;
            }
            String ch = String.valueOf(run.charAt(idx));
            if (SearchLexicon.STOP_WORDS.contains(ch)) {
                continue;
            }
            tokens.add(new Token(ch, WEIGHT_CHAR));
        }
        return tokens;
    }

    /** 去重后的词集合（文档侧使用） */
    public static Set<String> tokenizeToSet(String text, Collection<String> dictionary) {
        Set<String> set = new LinkedHashSet<>();
        for (Token token : tokenize(text, dictionary)) {
            set.add(token.text);
        }
        return set;
    }

    /** 提取关键词（按权重降序，去重），用于标签建议等场景 */
    public static List<String> extractKeywords(String text, Collection<String> dictionary, int limit) {
        List<Token> tokens = tokenize(text, dictionary);
        Set<String> seen = new LinkedHashSet<>();
        tokens.stream()
                .filter(token -> token.text().length() >= 2 && !isPureDigit(token.text()))
                .sorted((a, b) -> Integer.compare(b.weight(), a.weight()))
                .forEach(token -> seen.add(token.text()));
        return seen.stream().limit(limit).toList();
    }

    private static boolean isPureDigit(String text) {
        return text.chars().allMatch(Character::isDigit);
    }
}
