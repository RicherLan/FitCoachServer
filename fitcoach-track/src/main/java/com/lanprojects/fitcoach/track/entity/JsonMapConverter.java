package com.lanprojects.fitcoach.track.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

/**
 * JPA 字段级转换器：{@code Map<String, String>} ←→ JSON 字符串。
 *
 * <p>用途：埋点 {@code properties} 业务字段，TEXT 列存 JSON，业务侧仍以 Map 操作。
 *
 * <p>与 {@code feedback.JsonStringListConverter} 同模式：
 * <ul>
 *   <li>静态 {@link ObjectMapper}（线程安全，JPA 创建 Converter 时不走 Spring 注入）；</li>
 *   <li>null/empty 入库统一写 {@code "{}"}，避免客户端读到 null；</li>
 *   <li>反序列化异常兜底为空 Map，单条脏数据不影响批量查询。</li>
 * </ul>
 *
 * <p><b>跨模块复用</b>：将来 fitcoach-common 抽公共 JSON 转换器时可统一替换；
 * 短期内不同模块各自维护一份是可接受的（避免引入循环依赖与早期过度设计）。
 */
@Slf4j
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            log.error("埋点 properties 序列化失败: keys={}", attribute.keySet(), e);
            return "{}";
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, String> result = MAPPER.readValue(dbData, MAP_TYPE);
            return result == null ? Collections.emptyMap() : result;
        } catch (Exception e) {
            log.error("埋点 properties 反序列化失败: dbData={}", dbData, e);
            return Collections.emptyMap();
        }
    }
}
