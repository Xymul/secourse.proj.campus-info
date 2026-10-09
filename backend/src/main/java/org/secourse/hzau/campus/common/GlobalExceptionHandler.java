package org.secourse.hzau.campus.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把校验失败、业务异常、并发冲突、数据库约束等统一转换为友好提示。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException ex) {
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(ApiResponse.BAD_REQUEST, message.isEmpty() ? "参数校验失败" : message);
    }

    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBind(BindException ex) {
        String message = ex.getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(ApiResponse.BAD_REQUEST, message.isEmpty() ? "参数绑定失败" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraint(ConstraintViolationException ex) {
        return ApiResponse.fail(ApiResponse.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        return ApiResponse.fail(ApiResponse.BAD_REQUEST, "缺少必要参数：" + ex.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadable(HttpMessageNotReadableException ex) {
        return ApiResponse.fail(ApiResponse.BAD_REQUEST, "请求体格式错误");
    }

    /** 并发验证冲突：事务在乐观锁/唯一约束下回滚 */
    @ExceptionHandler({OptimisticLockingFailureException.class})
    public ApiResponse<Void> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("并发写入冲突: {}", ex.getMessage());
        return ApiResponse.fail(ApiResponse.BAD_REQUEST, "操作过于频繁，请稍后重试");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResponse<Void> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("数据完整性约束: {}", ex.getMostSpecificCause().getMessage());
        return ApiResponse.fail(ApiResponse.BAD_REQUEST, "数据校验未通过（可能重复提交或字段不符合约束）");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception ex) {
        log.error("系统异常", ex);
        return ApiResponse.fail(ApiResponse.SERVER_ERROR, "服务器内部错误：" + ex.getMessage());
    }
}
