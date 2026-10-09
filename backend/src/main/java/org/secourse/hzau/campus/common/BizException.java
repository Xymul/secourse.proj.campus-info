package org.secourse.hzau.campus.common;

import lombok.Getter;

/**
 * 业务异常：由 {@link GlobalExceptionHandler} 统一转换为响应体。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(ApiResponse.BAD_REQUEST, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException notFound(String message) {
        return new BizException(ApiResponse.NOT_FOUND, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(ApiResponse.UNAUTHORIZED, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ApiResponse.FORBIDDEN, message);
    }
}
