package com.lanprojects.fitcoach.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

/**
 * 国际化资源配置 — 所有"对客户端可见的提示文案"统一从这里取，按请求语言翻译后再下发。
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li><b>basename = classpath:i18n/messages</b>：约定所有翻译资源放
 *       {@code fitcoach-common/src/main/resources/i18n/messages_{lang}.properties}。
 *       fitcoach-common 是所有业务模块的公共依赖，资源集中放这里所有模块都能用。</li>
 *   <li><b>defaultLocale = SIMPLIFIED_CHINESE</b>：本项目主要受众是中文用户，
 *       客户端没传 X-Client-Lang / 传了不支持的语言时回落到中文，避免出现 key 字面量。</li>
 *   <li><b>useCodeAsDefaultMessage = false</b>：找不到 key 时直接抛 NoSuchMessageException，
 *       由 {@link I18nMessages} 兜底（fallback message → key），便于开发期排查"漏翻译"问题。
 *       上线后即便漏了某条 key，最坏情况也是返回兜底中文，不会把 key 字面量暴露给用户。</li>
 *   <li><b>cacheSeconds = -1</b>：永不刷新（生产环境）。dev 环境想看翻译热更可临时调小。</li>
 *   <li><b>fallbackToSystemLocale = false</b>：禁止回落到 JVM 默认 Locale，
 *       否则在不同时区/系统的服务器上行为会漂移；统一回落到上面配的 defaultLocale。</li>
 * </ul>
 */
@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        ms.setUseCodeAsDefaultMessage(false);
        ms.setFallbackToSystemLocale(false);
        ms.setCacheSeconds(-1);
        return ms;
    }
}
