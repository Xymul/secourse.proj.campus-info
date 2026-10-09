package org.secourse.hzau.campus.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.entity.AppUser;
import org.secourse.hzau.campus.entity.AuthToken;
import org.secourse.hzau.campus.entity.enums.UserStatus;
import org.secourse.hzau.campus.repository.AppUserRepository;
import org.secourse.hzau.campus.repository.AuthTokenRepository;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录与权限拦截器：
 *
 * <ul>
 *   <li>从 {@code Authorization: Bearer <token>} 解析登录态并写入 {@link CurrentUserContext}；</li>
 *   <li>白名单接口允许匿名访问（浏览地图、搜索、查看详情）；</li>
 *   <li>{@code /api/admin/**} 仅管理员可访问。</li>
 * </ul>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 完全公开的接口 */
    private static final List<String> PUBLIC_PATTERNS = List.of(
            "/api/auth/login", "/api/auth/register",
            "/api/schools", "/api/schools/**",
            "/api/categories", "/api/categories/**",
            "/api/locations", "/api/locations/**",
            "/api/meta/**"
    );

    /** 仅 GET 公开的接口（浏览与搜索） */
    private static final List<String> PUBLIC_GET_PATTERNS = List.of(
            "/api/posts", "/api/posts/map", "/api/posts/nearby", "/api/posts/hot",
            "/api/posts/*", "/api/posts/*/comments", "/api/posts/*/verifications", "/api/posts/*/clusters",
            "/api/search/**", "/api/hot/**"
    );

    /** 即使匹配上面的通配规则也必须登录 */
    private static final List<String> ALWAYS_PROTECTED = List.of(
            "/api/posts/mine", "/api/me/**", "/api/ai/**"
    );

    private final AuthTokenRepository authTokenRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthInterceptor(AuthTokenRepository authTokenRepository, AppUserRepository appUserRepository) {
        this.authTokenRepository = authTokenRepository;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String token = extractToken(request);
        CurrentUser user = token == null ? null : resolve(token);
        if (user != null) {
            CurrentUserContext.set(user);
        }

        if (isPublic(request, path)) {
            return true;
        }
        if (user == null) {
            writeError(response, ApiResponse.UNAUTHORIZED, "请先登录");
            return false;
        }
        if (path.startsWith("/api/admin") && !user.isAdmin()) {
            writeError(response, ApiResponse.FORBIDDEN, "需要管理员权限");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
    }

    private boolean isPublic(HttpServletRequest request, String path) {
        for (String pattern : ALWAYS_PROTECTED) {
            if (pathMatcher.match(pattern, path)) {
                return false;
            }
        }
        for (String pattern : PUBLIC_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        if (HttpMethod.GET.matches(request.getMethod())) {
            for (String pattern : PUBLIC_GET_PATTERNS) {
                if (pathMatcher.match(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        String token = request.getHeader("X-Token");
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }
        return token == null || token.isBlank() ? null : token.trim();
    }

    private CurrentUser resolve(String token) {
        AuthToken authToken = authTokenRepository.findById(token).orElse(null);
        if (authToken == null || authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        AppUser user = appUserRepository.findById(authToken.getUserId()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return null;
        }
        Long schoolId = user.getSchool() == null ? null : user.getSchool().getSchoolId();
        return new CurrentUser(user.getUserId(), user.getUsername(), user.getNickname(),
                user.getRole().name(), schoolId);
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(AppResponseError.of(code, message)));
    }

    /** 简化的错误响应载体，保证与 {@link ApiResponse} 字段一致 */
    private record AppResponseError(int code, String message, Object data) {
        static AppResponseError of(int code, String message) {
            return new AppResponseError(code, message, null);
        }
    }
}
