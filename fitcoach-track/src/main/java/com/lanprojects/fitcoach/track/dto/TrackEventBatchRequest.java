package com.lanprojects.fitcoach.track.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 批量上报请求体。
 *
 * <p>客户端 SDK 内部以「20 条 / 30 秒 / App 后台」任一触发刷一次，
 * 单次最多 {@code TrackService.MAX_BATCH_SIZE} 条；超过会被拒（HTTP 状态 200，业务码 8402）。
 */
@Data
@Schema(description = "批量埋点上报请求")
public class TrackEventBatchRequest {

    @Schema(description = "事件列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TrackEventItem> events;
}
