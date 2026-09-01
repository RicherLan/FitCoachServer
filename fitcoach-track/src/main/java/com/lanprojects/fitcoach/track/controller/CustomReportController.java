package com.lanprojects.fitcoach.track.controller;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.track.dto.CustomReportRequest;
import com.lanprojects.fitcoach.track.dto.CustomReportResponse;
import com.lanprojects.fitcoach.track.service.CustomReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 自定义报表接口
 *
 * 职责：
 * 1. 生成自定义报表（灵活选择事件、指标、分组维度）
 * 2. 保存/加载报表模板（Phase 3 待实现）
 * 3. 导出报表数据
 *
 * 权限：仅 Admin 角色可访问
 */
@RestController
@RequestMapping("/api/admin/track/report")
@RequiredArgsConstructor
public class CustomReportController {
    private final CustomReportService customReportService;

    /**
     * 生成自定义报表
     *
     * @param request 报表请求（包含事件、指标、分组、时间范围、筛选条件）
     * @return 报表数据（包含数据行、汇总行、生成时间）
     */
    @PostMapping("/generate")
    public Result<CustomReportResponse> generateReport(@RequestBody CustomReportRequest request) {
        CustomReportResponse response = customReportService.generateReport(request);
        return Result.success(response);
    }

    /**
     * 保存报表模板（Phase 3 待实现）
     *
     * @param templateName 模板名称
     * @param request 报表配置
     * @return 保存结果
     */
    @PostMapping("/template/save")
    public Result<String> saveTemplate(
            @RequestParam String templateName,
            @RequestBody CustomReportRequest request) {
        throw new BusinessException(ResultCode.FEATURE_NOT_IMPLEMENTED, "报表模板保存功能正在开发中");
    }

    /**
     * 加载报表模板（Phase 3 待实现）
     *
     * @param templateId 模板 ID
     * @return 报表配置
     */
    @GetMapping("/template/{templateId}")
    public Result<CustomReportRequest> loadTemplate(@PathVariable String templateId) {
        throw new BusinessException(ResultCode.FEATURE_NOT_IMPLEMENTED, "报表模板加载功能正在开发中");
    }

    /**
     * 列出所有报表模板（Phase 3 待实现）
     *
     * @return 模板列表
     */
    @GetMapping("/templates")
    public Result<Object> listTemplates() {
        throw new BusinessException(ResultCode.FEATURE_NOT_IMPLEMENTED, "报表模板列表功能正在开发中");
    }

    /**
     * 删除报表模板（Phase 3 待实现）
     *
     * @param templateId 模板 ID
     * @return 删除结果
     */
    @DeleteMapping("/template/{templateId}")
    public Result<String> deleteTemplate(@PathVariable String templateId) {
        throw new BusinessException(ResultCode.FEATURE_NOT_IMPLEMENTED, "报表模板删除功能正在开发中");
    }
}
