package com.lanprojects.fitcoach.admin.dto.trainingrecord;

import com.lanprojects.fitcoach.trainingrecord.dto.TrainingRecordDTO;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecord;
import lombok.Builder;
import lombok.Data;

/**
 * Admin 端「用户训练记录」详情 DTO — 在客户端版 {@link TrainingRecordDTO}
 * 之上加 user 维度（uid / nickname），其余三层结构（exercises / sets）完全复用。
 *
 * <p>这样前端 admin 详情弹窗能直接渲染所有动作与组数据。
 */
@Data
@Builder
public class AdminTrainingRecordDetailDto {

    /** 所属用户 id */
    private Long userId;

    /** 用户业务 uid（controller join 出来填上） */
    private String userUid;

    /** 用户昵称（controller join 出来填上） */
    private String userNickname;

    /** 训练记录完整内容（三层） */
    private TrainingRecordDTO record;

    /**
     * 从 entity 构造（user 字段留空，由 controller 后续 enrich）。
     */
    public static AdminTrainingRecordDetailDto from(TrainingRecord r) {
        return AdminTrainingRecordDetailDto.builder()
                .userId(r.getUserId())
                .record(TrainingRecordDTO.from(r))
                .build();
    }
}
