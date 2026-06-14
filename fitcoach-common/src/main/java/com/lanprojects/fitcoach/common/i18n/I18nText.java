package com.lanprojects.fitcoach.common.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanprojects.fitcoach.common.client.ClientContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「内容多语言」工具 — 用于数据库里存的「业务内容字段」（动作名称、肌群标题、动作描述等）。
 *
 * <p><b>设计动机</b>：
 * <ul>
 *   <li>{@link I18nMessages} 走 .properties 资源文件，适合「应用层固定文案」（错误码、按钮、提示），
 *       数量有限、变更走发版；</li>
 *   <li>而「动作元数据」是运营在 admin 后台维护的，新增/改名都不应该发版，
 *       所以必须把翻译跟着实体一起存数据库。本工具就是这个场景下的统一入口。</li>
 * </ul>
 *
 * <p><b>存储格式</b>：实体上加一列 {@code LONGTEXT} / {@code TEXT}，存形如：
 * <pre>{@code
 * {"zh-CN":"深蹲","en":"Squat","ja":"スクワット","ko":"스쿼트",
 *  "fr":"Squat","es":"Sentadilla","ru":"Приседания","ar":"القرفصاء"}
 * }</pre>
 *
 * <p><b>典型调用</b>：
 * <pre>{@code
 * String name = I18nText.pick(exercise.getDisplayNameI18n(), exercise.getDisplayName());
 * // 或显式 locale
 * String name = I18nText.pick(json, fallback, Locale.ENGLISH);
 * }</pre>
 *
 * <p><b>语言匹配链</b>（按当前 {@link ClientContext#locale()} 或显式 locale）：
 * <ol>
 *   <li>精确匹配：language-COUNTRY（如 "zh-CN"）；</li>
 *   <li>语言匹配：仅 language（"en" 匹配 en-US/en-GB 等任意条目）；</li>
 *   <li>回落 zh-CN（项目主语言）；</li>
 *   <li>回落 en（国际化常用兜底）；</li>
 *   <li>取 Map 的第一个非空值；</li>
 *   <li>都不行 → 用 {@code legacyFallback}（实体上的旧单语言字段）。</li>
 * </ol>
 * 这一兜底链保证「漏翻译某种语言」时仍能返回合理结果，永远不返回 null/key 字面量。
 *
 * <p><b>线程安全</b>：内部使用线程安全的 {@link ObjectMapper}（无状态），可放心并发调用。
 */
@Slf4j
public final class I18nText {

    /** 共享 ObjectMapper — Jackson 的 ObjectMapper 配置完毕后是线程安全的 */
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> MAP_TYPE =
            new TypeReference<>() {};

    /** 国际化兜底语言序列（找不到目标语言时按顺序尝试） */
    private static final List<String> FALLBACK_TAGS = List.of("zh-CN", "en");

    private I18nText() {}

    // ====== 业务入口 ======

    /**
     * 按当前请求语言（{@link ClientContext#locale()}）从 i18n JSON 中取值。
     *
     * @param i18nJson       多语言 JSON 字符串，可空（兼容历史数据）
     * @param legacyFallback 旧单语言字段（如 {@code exercise.displayName}）；
     *                       i18nJson 为空或语言全部缺失时使用
     * @return 解析后的文案；不会返回 null
     */
    public static String pick(String i18nJson, String legacyFallback) {
        return pick(i18nJson, legacyFallback, ClientContext.locale());
    }

    /**
     * 按显式 {@link Locale} 从 i18n JSON 中取值（用于非 HTTP 上下文场景，如定时任务）。
     */
    public static String pick(String i18nJson, String legacyFallback, Locale locale) {
        Map<String, String> map = parse(i18nJson);
        if (!map.isEmpty()) {
            String hit = resolveFromMap(map, locale);
            if (hit != null) {
                return hit;
            }
        }
        // 全部 miss → 用旧字段兜底；旧字段也空 → 返回空串（绝不返回 null）
        return legacyFallback != null ? legacyFallback : "";
    }

    /**
     * 把一组多语言文案打包成 JSON 串（Seeder/Admin 入库时使用）。
     * <p>传入的 map 会保留写入顺序（LinkedHashMap 序列化结果稳定，便于 review）。
     *
     * @param translations key 是 BCP-47 语言 tag（zh-CN/en/ja/...），value 是对应文案
     * @return JSON 字符串；map 为空时返回 null（让数据库列存 NULL，节省空间）
     */
    public static String toJson(Map<String, String> translations) {
        if (translations == null || translations.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(translations);
        } catch (Exception e) {
            log.warn("[i18n-text] serialize failed, keys={}", translations.keySet(), e);
            return null;
        }
    }

    // ====== 内部工具 ======

    /**
     * 把 JSON 串反序列化为有序 Map；解析失败 / 空串都返回空 Map（永远不抛异常）。
     */
    private static Map<String, String> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, String> parsed = MAPPER.readValue(json, MAP_TYPE);
            return parsed != null ? parsed : Collections.emptyMap();
        } catch (Exception e) {
            // 历史数据脏 / 手动改库出错都走这里 — 静默兜底，避免业务 500
            log.warn("[i18n-text] parse failed, json={}", abbreviate(json), e);
            return Collections.emptyMap();
        }
    }

    /**
     * 在多语言 Map 中按当前 locale 选取最佳匹配 — 见类注释「语言匹配链」。
     * @return 命中的文案；全部 miss 返回 null
     */
    private static String resolveFromMap(Map<String, String> map, Locale locale) {
        if (locale != null) {
            // 1. 精确 language-COUNTRY
            String exact = nonBlank(map.get(locale.toLanguageTag()));
            if (exact != null) return exact;

            // 2. 仅 language（en 匹配 en / en-US / 任意 en-*）
            String lang = locale.getLanguage();
            if (!lang.isEmpty()) {
                String byLang = nonBlank(map.get(lang));
                if (byLang != null) return byLang;
                // map 里如果只有 "en-US" 之类，也尝试用 startsWith 匹配
                for (Map.Entry<String, String> e : map.entrySet()) {
                    String k = e.getKey();
                    if (k != null && k.startsWith(lang + "-")) {
                        String v = nonBlank(e.getValue());
                        if (v != null) return v;
                    }
                }
            }
        }

        // 3-4. 回落主语言
        for (String tag : FALLBACK_TAGS) {
            String v = nonBlank(map.get(tag));
            if (v != null) return v;
        }

        // 5. Map 里的第一个非空值
        for (String v : map.values()) {
            String nv = nonBlank(v);
            if (nv != null) return nv;
        }
        return null;
    }

    private static String nonBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
