package com.lanprojects.fitcoach.track.controller;

import com.lanprojects.fitcoach.common.model.Result;
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
 * 2. 保存/加载报表模板
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
     * 保存报表模板
     *
     * @param templateName 模板名称
     * @param request 报表配置
     * @return 保存结果
     */
    @PostMapping("/template/save")
    public Result<String> saveTemplate(
            @RequestParam String templateName,
            @RequestBody CustomReportRequest request) {
        // TODO: Phase 3 实现模板持久化
        // 1. 创建 ReportTemplate 实体
        // 2. 保存到数据库
        // 3. 返回模板 ID
        return Result.success("模板保存成功");
    }

    /**
     * 加载报表模板
     *
     * @param templateId 模板 ID
     * @return 报表配置
     */
    @GetMapping("/template/{templateId}")
    public Result<CustomReportRequest> loadTemplate(@PathVariable String templateId) {
        // TODO: Phase 3 实现模板加载
        // 1. 从数据库查询模板
        // 2. 返回报表配置
        return Result.success(null);
    }

    /**
     * 列出所有报表模板
     *
     * @return 模板列表
     */
    @GetMapping("/templates")
    public Result<Object> listTemplates() {
        // TODO: Phase 3 实现模板列表
        // 1. 查询当前用户的所有模板
        // 2. 返回模板列表（包含名称、描述、创建时间）
        return Result.success(null);
    }

    /**
     * 删除报表模板
     *
     * @param templateId 模板 ID
     * @return 删除结果
     */
    @DeleteMapping("/template/{templateId}")
    public Result<String> deleteTemplate(@PathVariable String templateId) {
        // TODO: Phase 3 实现模板删除
        // 1. 从数据库删除模板
        // 2. 返回删除结果
        return Result.success("模板删除成功");
    }
}
