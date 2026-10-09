package org.secourse.hzau.campus.search;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自然语言搜索的意图解析结果（自实现「AI 智能搜索」的中间产物）。
 */
@Getter
@Setter
@NoArgsConstructor
public class SearchIntent {

    /** 原始查询语句 */
    private String rawQuery;
    /** 关键词（分词后按权重排序） */
    private List<String> keywords = new ArrayList<>();
    /** 识别到的分类编码 */
    private String categoryCode;
    /** 识别到的分类名称 */
    private String categoryName;
    /** 分类识别依据说明 */
    private List<String> categoryReasons = new ArrayList<>();
    /** 识别到的地点 */
    private Long locationId;
    private String locationName;
    /** 时间窗口（只保留该时间之后发布的信息） */
    private LocalDateTime createdAfter;
    private String timeLabel;
    /** 是否包含「附近」意图 */
    private boolean nearby;
    /** 距离半径（公里） */
    private Double radiusKm;
    /** 价格上限 */
    private Double maxPrice;
    /** 是否成功理解（用于降级判断） */
    private boolean understood;
    /** 降级说明（例如“未识别到明确条件，已按关键词检索”） */
    private String degradeReason;

    /** 供前端展示的意图解析结果 */
    public Map<String, Object> toSummary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rawQuery", rawQuery);
        map.put("keywords", keywords);
        map.put("categoryCode", categoryCode);
        map.put("categoryName", categoryName);
        map.put("categoryReasons", categoryReasons);
        map.put("locationId", locationId);
        map.put("locationName", locationName);
        map.put("timeLabel", timeLabel);
        map.put("nearby", nearby);
        map.put("radiusKm", radiusKm);
        map.put("maxPrice", maxPrice);
        map.put("understood", understood);
        map.put("degradeReason", degradeReason);
        return map;
    }

    /** 解析出的条件数量，用于计算“理解程度” */
    public int conditionCount() {
        int count = keywords.isEmpty() ? 0 : 1;
        count += categoryCode == null ? 0 : 1;
        count += locationId == null ? 0 : 1;
        count += createdAfter == null ? 0 : 1;
        count += nearby ? 1 : 0;
        count += maxPrice == null ? 0 : 1;
        return count;
    }
}
