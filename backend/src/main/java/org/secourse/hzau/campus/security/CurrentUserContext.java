package org.secourse.hzau.campus.security;

import org.secourse.hzau.campus.common.BizException;

/**
 * 线程级登录上下文：控制器与业务层通过它获取当前用户。
 */
public final class CurrentUserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 获取当前用户，未登录直接抛 401 */
    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw BizException.unauthorized("请先登录");
        }
        return user;
    }

    /** 获取当前用户（可能为 null，用于可选登录的查询接口） */
    public static CurrentUser optional() {
        return HOLDER.get();
    }

    public static Long requireUserId() {
        return require().userId();
    }

    public static Long optionalUserId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }
}
