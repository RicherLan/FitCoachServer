package com.lanprojects.fitcoach.trainingrecord.entity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanprojects.fitcoach.trainingrecord.dto.AiTrainingSummary;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
@Converter
public class AiTrainingSummaryConverter implements AttributeConverter<AiTrainingSummary, String> {
    private static final ObjectMapper JSON = new ObjectMapper();
    public String convertToDatabaseColumn(AiTrainingSummary value) {
        if (value == null) { return null; }
        try { return JSON.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid AI summary", e); }
    }
    public AiTrainingSummary convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) { return null; }
        try { return JSON.readValue(value, AiTrainingSummary.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid stored AI summary", e); }
    }
}
