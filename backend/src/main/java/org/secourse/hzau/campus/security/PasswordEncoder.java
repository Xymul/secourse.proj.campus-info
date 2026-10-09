package org.secourse.hzau.campus.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 轻量密码编码器（盐 + SHA-256 迭代）。
 *
 * <p>存储格式：{@code salt$hash}。生产环境可平滑替换为 BCrypt / Argon2。</p>
 */
@Component
public class PasswordEncoder {

    private static final int ITERATIONS = 1200;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String encode(String rawPassword) {
        String salt = randomSalt();
        return salt + "$" + hash(rawPassword, salt);
    }

    public boolean matches(String rawPassword, String encoded) {
        if (rawPassword == null || encoded == null) {
            return false;
        }
        int idx = encoded.indexOf('$');
        if (idx <= 0) {
            return false;
        }
        String salt = encoded.substring(0, idx);
        String expected = encoded.substring(idx + 1);
        return expected.equalsIgnoreCase(hash(rawPassword, salt));
    }

    private String randomSalt() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawPassword, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = (salt + "::" + rawPassword).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < ITERATIONS; i++) {
                digest.reset();
                value = digest.digest(value);
            }
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
