package org.secourse.hzau.campus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.secourse.hzau.campus.entity.enums.UserRole;
import org.secourse.hzau.campus.entity.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * 平台用户（校园普通用户 / 管理员）。
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(name = "username", nullable = false, length = 50, unique = true)
    private String username;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "avatar", length = 255)
    private String avatar;

    @Column(name = "contact", length = 100)
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /** 信用分：被证实加分、被证伪/被举报扣分，供后台运营参考 */
    @Column(name = "credit_score", nullable = false)
    private Integer creditScore = 100;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
