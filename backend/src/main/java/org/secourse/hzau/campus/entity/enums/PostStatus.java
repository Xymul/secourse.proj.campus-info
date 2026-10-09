package org.secourse.hzau.campus.entity.enums;

/**
 * 帖子状态（信息生命周期）。
 */
public enum PostStatus {
    /** 待审核（AI 辅助发布后进入审核队列） */
    PENDING,
    /** 已发布，可见可检索 */
    PUBLISHED,
    /** 审核未通过 */
    REJECTED,
    /** 管理员下架/违规移除 */
    REMOVED,
    /** 已过期（定时任务自动处理） */
    EXPIRED,
    /** 已解决（如失物已认领、二手已成交） */
    RESOLVED
}
