package com.lanprojects.fitcoach.feedback.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * JPA 字段级转换器：{@code List<String>} ←→ JSON 字符串。
 * <p>
 * 用途：反馈附件 URL 列表存到一个 TEXT 列里，避免单开 attachment 子表。
 * 数量级小（单条最多 5 个 URL），TEXT 列足够；后续若需要按附件维度统计，
 * 再迁移到子表也不影响 API 形态。
 * <p>
 * 静态 {@link ObjectMapper}：JPA 创建 Converter 实例时机不确定，
 * 不走 Spring 注入；ObjectMapper 线程安全，可全局共用。
 */
@Slf4j
@Converter
public class JsonStringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            // 入库空数组而非 null，简化客户端读取（永远是数组）
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            // 序列化基本不会失败（List<String>），但兜底避免业务整个挂掉
            log.error("反馈附件列表序列化失败: {}", attribute, e);
            return "[]";
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> result = MAPPER.readValue(dbData, LIST_TYPE);
            return result == null ? Collections.emptyList() : result;
        } catch (Exception e) {
            // 历史脏数据兜底：不让单条坏数据冲垮整个查询接口
            log.error("反馈附件列表反序列化失败: dbData={}", dbData, e);
            return Collections.emptyList();
        }
    }
}
