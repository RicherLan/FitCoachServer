package com.lanprojects.fitcoach.common.exception;

import com.lanprojects.fitcoach.common.model.ResultCode;
import lombok.Getter;

/**
 * 业务异常 — 用于业务逻辑中的可预期错误
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
