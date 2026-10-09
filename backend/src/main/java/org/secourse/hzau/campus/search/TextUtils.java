package org.secourse.hzau.campus.search;

import java.util.Collection;
import java.util.Locale;

/**
 * 文本与地理计算工具方法（供搜索、发布辅助、治理等模块复用）。
 */
public final class TextUtils {

    private TextUtils() {
    }

    /** 归一化：转小写，英文标点与空白统一为空格，便于分词与匹配 */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isCjk(c) || isAsciiWord(c)) {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    public static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    public static boolean isAsciiWord(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z');
    }

    public static boolean containsIgnoreCase(String text, String keyword) {
        return text != null && keyword != null && text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    public static boolean containsAny(String text, Collection<String> keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static String firstMatched(String text, Collection<String> keywords) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        String hit = null;
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))
                    && (hit == null || keyword.length() > hit.length())) {
                hit = keyword;
            }
        }
        return hit;
    }

    public static int countOccurrences(String text, String keyword) {
        if (text == null || keyword == null || keyword.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = text.indexOf(keyword);
        while (idx >= 0) {
            count++;
            idx = text.indexOf(keyword, idx + keyword.length());
        }
        return count;
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 提取「数字 + 单位」中的数字，如 “预算300元以内” -> 300 */
    public static Double numberBeforeUnit(String text, String unit) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int unitIdx = text.indexOf(unit);
        if (unitIdx <= 0) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = unitIdx - 1; i >= 0 && digits.length() < 6; i--) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits.insert(0, c);
            } else if (c == '.' && digits.length() > 0) {
                digits.insert(0, c);
            } else {
                break;
            }
        }
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(digits.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 球面距离（公里），与 SQL 中的 Haversine 实现保持一致 */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** 联系方式脱敏：手机号中间四位打码，QQ/微信只保留首尾字符 */
    public static String maskContact(String contact) {
        if (contact == null || contact.isBlank()) {
            return contact;
        }
        String trimmed = contact.trim();
        if (trimmed.matches("\\d{11}")) {
            return trimmed.substring(0, 3) + "****" + trimmed.substring(7);
        }
        if (trimmed.matches("\\d{5,10}")) {
            return trimmed.length() <= 3 ? "***" : trimmed.substring(0, 2) + "***" + trimmed.substring(trimmed.length() - 1);
        }
        if (trimmed.length() > 4) {
            return trimmed.charAt(0) + "***" + trimmed.charAt(trimmed.length() - 1);
        }
        return "***";
    }

    /** 是否包含疑似联系方式（手机号 / QQ / 微信） */
    public static boolean looksLikeContact(String text) {
        if (text == null) {
            return false;
        }
        return text.matches(".*1[3-9]\\d{9}.*")
                || text.matches(".*[Qq][Qq]\\s*[:：]?\\s*\\d{5,12}.*")
                || text.matches(".*(微信|vx|VX|wechat|WeChat)\\s*[:：]?\\s*[A-Za-z0-9_-]{4,}.*");
    }

    public static String joinTags(Collection<String> tags) {
        return tags == null || tags.isEmpty() ? "" : String.join(",", tags);
    }
}
