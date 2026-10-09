package org.secourse.hzau.campus.search;

import lombok.Getter;
import lombok.Setter;
import org.secourse.hzau.campus.entity.Post;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条检索结果：帖子 + 打分 + 距离 + 匹配原因（可解释性）。
 */
@Getter
@Setter
public class SearchHit {

    private Post post;
    private double score;
    /** 归一化相关度（0-100，便于前端展示） */
    private int relevance;
    /** 与用户的距离（公里），未提供定位时为 null */
    private Double distanceKm;
    /** 匹配原因说明 */
    private List<String> reasons = new ArrayList<>();

    public SearchHit(Post post) {
        this.post = post;
    }

    public void addReason(String reason) {
        if (reasons.size() < 4 && reason != null && !reason.isBlank()) {
            reasons.add(reason);
        }
    }
}
