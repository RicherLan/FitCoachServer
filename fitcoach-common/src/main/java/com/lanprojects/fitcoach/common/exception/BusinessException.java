package com.lanprojects.fitcoach.common.exception;

import com.lanprojects.fitcoach.common.model.ResultCode;
import lombok.Getter;

/**
 * 业务异常 — 用于业务逻辑中的可预期错误。
 *
 * <p><b>i18n 设计</b>：异常对象本身仅持有 <em>不依赖语言</em> 的元数据（i18nKey + args + fallback），
 * 真正翻译为用户语言发生在 {@link com.lanprojects.fitcoach.common.exception.GlobalExceptionHandler} 拼装
 * Result 时（按 {@link com.lanprojects.fitcoach.common.client.ClientContext#locale()}）。这样：
 * <ul>
 *   <li>同一个异常在不同请求语言下能产出不同 message，无需在抛出处感知 Locale；</li>
 *   <li>service 层可以用 {@code throw new BusinessException(ResultCode.XXX, args...)}
 *       传入参数化文案（messages 文件里写 {@code {0} / {1}} 占位符）；</li>
 *   <li>对于 {@code throw new BusinessException("自定义中文")} 这种"已经是最终文案"的旧用法，
 *       i18nKey 为 null，handler 直接透传不翻译，保持向后兼容。</li>
 * </ul>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    /**
     * i18n key — null 表示"message 已是最终文案，不再翻译"。
     * 通常由构造函数从 {@link ResultCode#getI18nKey()} 取得。
     */
    private final String i18nKey;

    /**
     * MessageFormat 占位符参数（{@code {0}/{1}/...}），可空。
     * 例如 messages 里 key 写 {@code "余额不足，还需 {0} 元"}，则 args = new Object[]{ 99.5 }。
     */
    private final Object[] args;

    // ====== 构造函数 ======

    /**
     * 仅传 message 的旧用法：i18nKey=null，handler 不翻译，直接下发该 message。
     * <p>新代码尽量避免这种用法，应该用 {@link #BusinessException(ResultCode)}。
     */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
        this.i18nKey = null;
        this.args = null;
    }

    /**
     * 标准用法：从 {@link ResultCode} 取 code + i18nKey，handler 会按请求语言翻译。
     * <p>构造时 super(rc.getMessage()) 写入的是 zh-CN 兜底文案，仅在 properties 全部漏配时被使用。
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.i18nKey = resultCode.getI18nKey();
        this.args = null;
    }

    /**
     * 参数化文案：支持 messages 文件里的 {0}/{1}/... 占位符。
     * <p>例如 {@code throw new BusinessException(ResultCode.XXX, userName, balance)}。
     */
    public BusinessException(ResultCode resultCode, Object... args) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.i18nKey = resultCode.getI18nKey();
        this.args = args;
    }

    /**
     * 显式覆盖 message：调用者自行控制最终文案，i18nKey 不会被翻译（避免 message vs 翻译结果二选一的歧义）。
     * <p>典型场景：参数校验时把字段名拼到 message 里，但又想用 BAD_REQUEST 这个 code。
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.i18nKey = null;
        this.args = null;
    }
}
