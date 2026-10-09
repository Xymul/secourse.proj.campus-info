package org.secourse.hzau.campus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 登录令牌（轻量会话）：生产环境可替换为 Redis / JWT。
 */
@Entity
@Table(name = "auth_token")
@Getter
@Setter
@NoArgsConstructor
public class AuthToken {

    @Id
    @Column(name = "token", length = 64)
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_access_at")
    private LocalDateTime lastAccessAt = LocalDateTime.now();

    public AuthToken(String token, Long userId, LocalDateTime expiresAt) {
        this.token = token;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
}
