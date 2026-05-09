package com.lanprojects.fitcoach.common.i18n;

import com.lanprojects.fitcoach.common.client.ClientContext;
import com.lanprojects.fitcoach.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 翻译工具 — 业务侧统一通过这里把 i18n key 翻译成"当前请求语言"的最终文案。
 *
 * <p><b>典型调用</b>：
 * <pre>{@code
 * @Autowired private I18nMessages i18nMessages;
 *
 * // 1. 直接翻译 ResultCode（最常用，等同于"按客户端语言翻译错误码 message"）
 * String msg = i18nMessages.translate(ResultCode.MEMBERSHIP_REQUIRED);
 *
 * // 2. 翻译 + 占位符参数（messages_*.properties 里 key 写成 "{0} 的余额不足"）
 * String msg = i18nMessages.translate("balance.insufficient", new Object[]{ phone });
 *
 * // 3. 翻译指定 key + 显式给兜底（很少用，一般走 ResultCode.message 兜底就够了）
 * String msg = i18nMessages.translate(key, args, locale, "兜底中文");
 * }</pre>
 *
 * <p><b>语言来源</b>：默认从 {@link ClientContext#locale()} 取，即 X-Client-Lang 头解析得到的 Locale。
 * 拦截器未注册路径（如 /api/admin/**）下 Locale 永远是 zh_CN，因此 admin 后台天然只会看到中文，符合预期。
 *
 * <p><b>找不到 key 的兜底链</b>：
 * <ol>
 *   <li>请求语言文件里有 → 返回该翻译；</li>
 *   <li>没有 → 退回 zh_CN 的同名 key；</li>
 *   <li>还没有 → 返回 ResultCode 内置的中文 message（{@link ResultCode#getMessage()}）；</li>
 *   <li>还没有 → 返回 key 字面量（不至于让接口返回 null/抛异常）。</li>
 * </ol>
 * 第 2 步由 Spring MessageSource 自动完成（基于 defaultLocale=zh_CN），第 3-4 步本类负责。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class I18nMessages {

    private final MessageSource messageSource;

    // ====== 高频快捷方法 ======

    /**
     * 翻译一个 ResultCode：以请求语言为目标，找不到时回落到 ResultCode 自带的中文 message。
     * <p>等价于 {@code translate(rc.getI18nKey(), null, ClientContext.locale(), rc.getMessage())}。
     */
    public String translate(ResultCode rc) {
        return translate(rc, (Object[]) null);
    }

    /**
     * 翻译一个 ResultCode + 占位符参数。
     * <p>占位符走 java.text.MessageFormat 语法（{0}/{1}/...），见 {@link MessageSource#getMessage}。
     */
    public String translate(ResultCode rc, Object... args) {
        if (rc == null) {
            return "";
        }
        return translate(rc.getI18nKey(), args, ClientContext.locale(), rc.getMessage());
    }

    /**
     * 仅翻译一个 i18n key（无占位符），按当前请求语言。
     * <p>找不到时返回 key 字面量本身——这种用法仅适合"基础设施层"（不属于任何 ResultCode 的提示文案）。
     */
    public String translate(String key) {
        return translate(key, null, ClientContext.locale(), key);
    }

    /**
     * 翻译一个 i18n key + 占位符。找不到时返回 key 字面量。
     */
    public String translate(String key, Object[] args) {
        return translate(key, args, ClientContext.locale(), key);
    }

    // ====== 完整签名（业务一般不直接用） ======

    /**
     * 完整翻译入口。
     *
     * @param key      i18n key（如 "auth.unauthorized"），null/空 → 直接返回 fallback
     * @param args     MessageFormat 占位符参数，可空
     * @param locale   目标语言，null → fallback 到 zh_CN
     * @param fallback 找不到 key 时的兜底文案，可空
     * @return 翻译后的文案，永远非 null
     */
    public String translate(String key, Object[] args, Locale locale, String fallback) {
        if (key == null || key.isBlank()) {
            // 业务传了空 key，直接返回兜底（避免 MessageSource 报 IllegalArgumentException）
            return safeFallback(fallback, "");
        }
        Locale target = locale != null ? locale : Locale.SIMPLIFIED_CHINESE;
        try {
            return messageSource.getMessage(key, args, target);
        } catch (NoSuchMessageException e) {
            // properties 漏配 — 开发期能发现，生产兜底返回中文 message 或 key 字面量
            log.warn("i18n key missing: key={}, locale={}, fallback={}", key, target, fallback);
            return safeFallback(fallback, key);
        }
    }

    private static String safeFallback(String fallback, String last) {
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return last;
    }
}
