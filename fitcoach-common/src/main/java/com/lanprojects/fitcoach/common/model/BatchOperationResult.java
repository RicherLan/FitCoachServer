package com.lanprojects.fitcoach.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量操作的统一结果体，跨模块共享。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>affected：实际成功处理的条数（按 id 去重后）；</li>
 *   <li>missing：请求里有但 DB 不存在 / 被前置过滤的 id 列表，便于前端二次提示；</li>
 *   <li>整体走 2xx：只要请求合法（参数校验通过）就成功，单条不存在不抛 4xx —— 批量语义里"部分成功"是常态。</li>
 * </ul>
 * <p>说明：放在 fitcoach-common.model 而非具体业务模块，方便 admin / log 等模块共享 DTO 形状。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResult {

    /** 实际成功处理的条数 */
    private int affected;

    /** 请求里但 DB 不存在的 id 列表（去重，可能为空） */
    private List<Long> missing;

    public static BatchOperationResult of(int affected, List<Long> missing) {
        return new BatchOperationResult(affected, missing == null ? List.of() : missing);
    }
}
