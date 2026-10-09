package org.secourse.hzau.campus.security;

/**
 * 当前登录用户信息（由拦截器从令牌解析后放入线程上下文）。
 */
public record CurrentUser(Long userId, String username, String nickname, String role, Long schoolId) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
