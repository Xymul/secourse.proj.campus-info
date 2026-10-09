package org.secourse.hzau.campus.service;

import org.secourse.hzau.campus.common.ApiResponse;
import org.secourse.hzau.campus.common.BizException;
import org.secourse.hzau.campus.config.AppProperties;
import org.secourse.hzau.campus.dto.AuthDtos;
import org.secourse.hzau.campus.entity.AppUser;
import org.secourse.hzau.campus.entity.AuthToken;
import org.secourse.hzau.campus.entity.School;
import org.secourse.hzau.campus.entity.enums.UserRole;
import org.secourse.hzau.campus.entity.enums.UserStatus;
import org.secourse.hzau.campus.repository.AppUserRepository;
import org.secourse.hzau.campus.repository.AuthTokenRepository;
import org.secourse.hzau.campus.repository.SchoolRepository;
import org.secourse.hzau.campus.security.CurrentUserContext;
import org.secourse.hzau.campus.security.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 账号与登录服务：注册、登录、登出、当前用户信息。
 */
@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final AuthTokenRepository authTokenRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final AppProperties properties;

    public AuthService(AppUserRepository appUserRepository,
                       AuthTokenRepository authTokenRepository,
                       SchoolRepository schoolRepository,
                       PasswordEncoder passwordEncoder,
                       OperationLogService operationLogService,
                       AppProperties properties) {
        this.appUserRepository = appUserRepository;
        this.authTokenRepository = authTokenRepository;
        this.schoolRepository = schoolRepository;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.properties = properties;
    }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        AppUser user = appUserRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> BizException.unauthorized("用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw BizException.forbidden("账号已被禁用，请联系管理员");
        }
        String token = issueToken(user);
        operationLogService.record(user.getUserId(), user.getNickname(), "LOGIN", "USER", user.getUserId(), "用户登录成功");
        return new AuthDtos.LoginResponse(token, toProfile(user));
    }

    @Transactional
    public AuthDtos.LoginResponse register(AuthDtos.RegisterRequest request) {
        if (!properties.getAuth().isAllowRegister()) {
            throw new BizException("当前未开放自助注册");
        }
        String username = request.username().trim();
        if (appUserRepository.existsByUsername(username)) {
            throw new BizException("用户名已被占用");
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setNickname(request.nickname().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreditScore(100);
        user.setContact(request.contact());
        if (request.schoolId() != null) {
            School school = schoolRepository.findById(request.schoolId())
                    .orElseThrow(() -> BizException.notFound("学校不存在"));
            user.setSchool(school);
        }
        appUserRepository.save(user);
        operationLogService.record(user.getUserId(), user.getNickname(), "REGISTER", "USER", user.getUserId(),
                "新用户注册：" + username);
        return new AuthDtos.LoginResponse(issueToken(user), toProfile(user));
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        authTokenRepository.findById(token).ifPresent(authToken -> {
            authTokenRepository.delete(authToken);
            operationLogService.record(authToken.getUserId(), null, "LOGOUT", "USER", authToken.getUserId(), "用户退出登录");
        });
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserProfile currentProfile() {
        AppUser user = appUserRepository.findById(CurrentUserContext.requireUserId())
                .orElseThrow(() -> BizException.unauthorized("登录状态已失效"));
        return toProfile(user);
    }

    @Transactional(readOnly = true)
    public AppUser requireUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new BizException(ApiResponse.NOT_FOUND, "用户不存在"));
    }

    private String issueToken(AppUser user) {
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(properties.getAuth().getTokenTtlHours());
        authTokenRepository.save(new AuthToken(token, user.getUserId(), expiresAt));
        return token;
    }

    private AuthDtos.UserProfile toProfile(AppUser user) {
        return new AuthDtos.UserProfile(user.getUserId(), user.getUsername(), user.getNickname(), user.getAvatar(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getSchool() == null ? null : user.getSchool().getSchoolId(),
                user.getSchool() == null ? null : user.getSchool().getName(),
                user.getCreditScore(), user.getContact());
    }
}
