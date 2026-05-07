package com.lanprojects.fitcoach.common.exception;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@Valid + @RequestBody）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 参数绑定异常（@Valid + 表单参数）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("参数绑定失败");
        log.warn("参数绑定失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * Multipart 文件超出 spring.servlet.multipart.max-file-size — 比业务校验更早触发。
     * <p>客户端通常已本地压缩，触发到这里说明压缩失败 / 恶意请求；
     * 用 5102 错误码与 AVATAR_FILE_TOO_LARGE 对齐，前端可统一展示。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过 multipart 限制: {}", e.getMessage());
        return Result.error(ResultCode.AVATAR_FILE_TOO_LARGE);
    }

    /**
     * 未知异常（兜底）
     * <p>
     * 默认只打类名 + message，避免完整堆栈泄露内部结构到日志系统；
     * 如需排查，把 com.lanprojects.fitcoach 的日志级别调到 DEBUG 才打印堆栈。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        log.error("未知异常: type={}, message={}", e.getClass().getSimpleName(), e.getMessage());
        if (log.isDebugEnabled()) {
            log.debug("未知异常堆栈", e);
        }
        return Result.error(ResultCode.ERROR);
    }
}
