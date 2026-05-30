package com.lanprojects.fitcoach.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

/**
 * 统一 API 响应封装。
 *
 * <p><b>traceId</b>（P2-5）：当返回错误时自动从 MDC 取值并下发，便于前端把 traceId 一起上报排错。
 * 成功响应默认不携带（{@code @JsonInclude NON_NULL} 会过滤掉 null）。
 * MDC 由 {@link com.lanprojects.fitcoach.common.trace.TraceIdFilter} 在请求入口注入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 与 {@link com.lanprojects.fitcoach.common.trace.TraceIdFilter#MDC_KEY} 保持一致 */
    private static final String MDC_TRACE_KEY = "traceId";

    private int code;
    private String message;
    private T data;
    /** 仅 error 路径自动填充，success 默认 null（被 @JsonInclude 过滤） */
    private String traceId;

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ====== 成功 ======

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    // ====== 失败 ======

    public static <T> Result<T> error(String message) {
        return errorWithTrace(ResultCode.ERROR.getCode(), message);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return errorWithTrace(resultCode.getCode(), resultCode.getMessage());
    }

    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return errorWithTrace(resultCode.getCode(), message);
    }

    public static <T> Result<T> error(int code, String message) {
        return errorWithTrace(code, message);
    }

    /** 内部统一：所有 error 静态工厂走这里，自动注入当前 MDC 的 traceId */
    private static <T> Result<T> errorWithTrace(int code, String message) {
        Result<T> r = new Result<>(code, message, null);
        String traceId = MDC.get(MDC_TRACE_KEY);
        if (traceId != null && !traceId.isEmpty()) {
            r.setTraceId(traceId);
        }
        return r;
    }
}
