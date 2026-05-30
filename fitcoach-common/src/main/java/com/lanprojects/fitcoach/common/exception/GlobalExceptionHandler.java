package com.lanprojects.fitcoach.common.exception;

import com.lanprojects.fitcoach.common.i18n.I18nMessages;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器（P2-5 分级版）。
 *
 * <p><b>i18n 策略</b>：所有错误码的 message 在拼装 Result 之前统一通过
 * {@link I18nMessages#translate(ResultCode)} 按
 * {@link com.lanprojects.fitcoach.common.client.ClientContext#locale()} 翻译为客户端语言；
 * 拦截器未注册（admin / 调试请求）时 Locale 自动落回 zh_CN。
 *
 * <p>对 {@code BusinessException} 持有自定义 message（i18nKey=null）的旧用法直接透传，不再翻译，
 * 保持向后兼容。
 *
 * <p><b>分级原则</b>：
 * <ul>
 *   <li>4xx 客户端错误（业务异常、参数校验、不支持的方法/媒体类型、404）→ {@code WARN}
 *       且不打堆栈，避免噪音；</li>
 *   <li>5xx 服务端错误（未知异常）→ {@code ERROR} + 完整堆栈，必须能在生产排错；</li>
 *   <li>所有日志默认带 {@code traceId}（由 {@link com.lanprojects.fitcoach.common.trace.TraceIdFilter}
 *       注入 MDC，配合 {@code logback %X{traceId}} pattern 自动出现）。</li>
 * </ul>
 *
 * <p>{@link Result#error} 静态工厂也会自动从 MDC 拿 traceId 一起下发给前端，
 * 用户复现问题时把 traceId 反馈给我们 → 后端直接 grep 一行日志定位上下文。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final I18nMessages i18nMessages;

    /**
     * 业务异常：优先按 i18nKey + args 翻译，缺失时回落到异常自带的 message（即 ResultCode 的 zh-CN 兜底）。
     * <p>分级：WARN（业务流转正常的预期异常，不打堆栈）。
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        String message = (e.getI18nKey() != null)
                ? i18nMessages.translate(e.getI18nKey(), e.getArgs(), com.lanprojects.fitcoach.common.client.ClientContext.locale(), e.getMessage())
                : e.getMessage();
        log.warn("业务异常 path={} code={} i18nKey={} message={}",
                request.getRequestURI(), e.getCode(), e.getI18nKey(), message);
        return Result.error(e.getCode(), message);
    }

    /**
     * 参数校验异常（@Valid + @RequestBody）。
     * <p>错误信息含字段名 + 校验注解 message，前缀走 i18n（{@code error.bad_request}）。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(i18nMessages.translate(ResultCode.BAD_REQUEST));
        log.warn("参数校验失败: {}", detail);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), detail);
    }

    /**
     * 参数绑定异常（@Valid + 表单参数）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBindException(BindException e) {
        String detail = e.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(i18nMessages.translate(ResultCode.BAD_REQUEST));
        log.warn("参数绑定失败: {}", detail);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), detail);
    }

    /**
     * Multipart 文件超出 spring.servlet.multipart.max-file-size — 比业务校验更早触发。
     * <p>客户端通常已本地压缩，触发到这里说明压缩失败 / 恶意请求 / 走错接口。
     * 用通用的 UPLOAD_FILE_TOO_LARGE，避免对头像 / 反馈附件等不同场景下文案不准。
     * 各业务（头像 5102 / 反馈附件 6102）在 service 层做更细粒度的校验。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过 multipart 限制: {}", e.getMessage());
        return Result.error(ResultCode.UPLOAD_FILE_TOO_LARGE.getCode(),
                i18nMessages.translate(ResultCode.UPLOAD_FILE_TOO_LARGE));
    }

    /**
     * 4xx 客户端错误的合集 — 都视为预期错误，统一 WARN，不打堆栈：
     * <ul>
     *   <li>{@link HttpRequestMethodNotSupportedException}：GET 接口被 POST 等</li>
     *   <li>{@link HttpMediaTypeNotSupportedException}：Content-Type 不匹配</li>
     *   <li>{@link MissingServletRequestParameterException}：缺必填 query param</li>
     *   <li>{@link MethodArgumentTypeMismatchException}：类型不匹配（如 String → Long）</li>
     *   <li>{@link HttpMessageNotReadableException}：body 不可解析 / JSON 损坏</li>
     *   <li>{@link NoHandlerFoundException}：404（需要 spring.mvc.throw-exception-if-no-handler-found=true）</li>
     * </ul>
     */
    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            NoHandlerFoundException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleClientError(Exception e, HttpServletRequest request) {
        log.warn("客户端请求错误 path={} type={} message={}",
                request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST.getCode(),
                i18nMessages.translate(ResultCode.BAD_REQUEST));
    }

    /**
     * 未知异常（兜底） — 服务端 bug，ERROR 级别 + 打完整堆栈。
     * <p>响应里只回通用提示文案，避免泄露内部结构；前端把 {@code traceId} 一并上报给我们，
     * 后端 grep 日志即可在堆栈里定位到具体行号。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("未知异常 path={} type={} message={}",
                request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage(), e);
        return Result.error(ResultCode.ERROR.getCode(),
                i18nMessages.translate(ResultCode.ERROR));
    }
}
