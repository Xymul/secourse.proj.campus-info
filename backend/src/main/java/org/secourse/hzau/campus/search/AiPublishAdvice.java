package org.secourse.hzau.campus.search;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 「AI 辅助发布」建议结果：分类 / 标签 / 有效期 / 风险提示，用户可自由修改后提交。
 */
@Getter
@Setter
public class AiPublishAdvice {

    /** 建议分类编码 */
    private String categoryCode;
    /** 建议分类名称 */
    private String categoryName;
    /** 置信度 0-1 */
    private double categoryConfidence;
    /** 分类建议依据 */
    private String categoryReason;
    /** 建议标签 */
    private List<String> tags = new ArrayList<>();
    /** 建议有效期（天） */
    private int suggestedValidDays = 7;
    /** 有效期依据 */
    private String validDaysReason;
    /** 命中的敏感词 */
    private List<String> riskWords = new ArrayList<>();
    /** 风险等级 LOW / MEDIUM / HIGH */
    private String riskLevel = "LOW";
    /** 是否检测到正文中的联系方式 */
    private boolean contactDetected;
    /** 综合建议说明 */
    private String summary;
    /** 逐条提示 */
    private List<String> tips = new ArrayList<>();
}
