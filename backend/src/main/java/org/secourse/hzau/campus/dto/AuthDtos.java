package org.secourse.hzau.campus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 账号与鉴权相关对象。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空") @Size(min = 3, max = 50, message = "用户名长度 3-50") String username,
            @NotBlank(message = "密码不能为空") @Size(min = 6, max = 64, message = "密码长度 6-64") String password,
            @NotBlank(message = "昵称不能为空") @Size(max = 50, message = "昵称不超过50字") String nickname,
            Long schoolId,
            String contact
    ) {
    }

    public record LoginResponse(String token, UserProfile user) {
    }

    public record UserProfile(
            Long userId,
            String username,
            String nickname,
            String avatar,
            String role,
            String status,
            Long schoolId,
            String schoolName,
            Integer creditScore,
            String contact
    ) {
    }
}
