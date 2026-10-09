package org.secourse.hzau.campus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.secourse.hzau.campus.entity.enums.VerificationResult;

import java.time.LocalDateTime;

/**
 * 协同验证记录：同一用户对同一帖子只能验证一次（状态互斥，由唯一约束保证）。
 */
@Entity
@Table(name = "verification", uniqueConstraints = {
        @UniqueConstraint(name = "uk_verification_post_user", columnNames = {"post_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Verification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private VerificationResult result;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
