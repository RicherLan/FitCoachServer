package com.lanprojects.fitcoach.trainingrecord.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** 客户端识别摘要，不含视频或原始骨骼帧；属于用户训练记录而非医疗评估。 */
@Data
public class AiTrainingSummary {
    @Min(1) @Max(1) private int schemaVersion = 1;
    @NotBlank @Size(max = 64) private String exerciseKey;
    @NotBlank @Pattern(regexp = "mlkit|mediapipe-2d|mediapipe-3d") private String model;
    @NotBlank @Pattern(regexp = "reps|hold") private String countMode = "reps";
    @NotEmpty @Size(max = 50) @Valid private List<@NotNull SetSummary> sets;
    @Data
    public static class SetSummary {
        @Min(0) @Max(10000) private int recognizedReps;
        @Min(1) @Max(10000) private int confirmedReps;
        @Min(0) @Max(86400000) private long activeMs;
        @DecimalMin("0") @DecimalMax("86400000") private Double meanRepMs;
        @DecimalMin("0") @DecimalMax("180") private Double rangeDegrees;
        @DecimalMin("0") @DecimalMax("1") private double validFrameRatio;
        @Size(max = 20) private List<@NotBlank @Size(max = 100) String> feedback;
    }
}
