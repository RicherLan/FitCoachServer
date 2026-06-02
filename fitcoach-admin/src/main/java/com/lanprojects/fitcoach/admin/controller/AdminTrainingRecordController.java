package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.trainingrecord.AdminTrainingRecordDetailDto;
import com.lanprojects.fitcoach.admin.dto.trainingrecord.AdminTrainingRecordDto;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.util.CsvHttpResponseUtil;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecord;
import com.lanprojects.fitcoach.trainingrecord.service.TrainingRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台「用户训练记录」只读管理。
 *
 * <p>路径前缀：{@code /api/admin/training-records}
 * <ul>
 *   <li>{@code GET /} — 分页列表，支持 userId / 日期范围筛选</li>
 *   <li>{@code GET /{id}} — 详情（含三层 exercises + sets）</li>
 *   <li>{@code GET /export} — CSV 导出（按筛选条件，最多 {@link #MAX_EXPORT_SIZE} 条）</li>
 * </ul>
 *
 * <p><b>只读理由</b>：训练记录是用户自己的核心数据，admin 不应该改 / 删它，
 * 仅做"查看 + 客服支持"用途。如真需要删，走数据库直连或单独的 DSR（数据合规）流程。
 *
 * <p><b>审计</b>：列表 / 详情查询不写审计（量大且无副作用）；CSV 导出会写审计（涉及大量数据导出）。
 */
@Slf4j
@Tag(name = "后台-用户训练记录", description = "用户训练记录只读查询 / CSV 导出")
@RestController
@RequestMapping("/api/admin/training-records")
@RequiredArgsConstructor
public class AdminTrainingRecordController {

    /** 单次 CSV 导出最大条数，避免拖垮 DB / 内存 */
    private static final int MAX_EXPORT_SIZE = 10_000;

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TrainingRecordService trainingRecordService;
    private final UserRepository userRepository;
    private final AdminAuditLogService auditLogService;

    /**
     * 训练记录分页列表。
     *
     * <p><b>筛选语义</b>（参考 {@link TrainingRecordService#pageForAdmin}）：
     * <ul>
     *   <li>传 {@code userId} → 按用户过滤（忽略日期范围）</li>
     *   <li>传 {@code start} + {@code end} → 按日期范围过滤（不带用户）</li>
     *   <li>都不传 → 全量分页</li>
     * </ul>
     *
     * @param page  1-based 页码（与 antd Table 习惯对齐）
     * @param size  每页条数（1-200）
     * @param userId 可选用户 id
     * @param start  可选日期范围起始（包含）
     * @param end    可选日期范围结束（包含）
     */
    @GetMapping
    public Result<PageResponse<AdminTrainingRecordDto>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        // 1-based → 0-based（service 接收 0-based）
        int p = Math.max(page, 1) - 1;
        int s = Math.min(Math.max(size, 1), 200);

        Page<TrainingRecord> recordPage = trainingRecordService.pageForAdmin(userId, start, end, p, s);
        Map<Long, User> userMap = batchLoadUsers(recordPage.getContent());
        return Result.success(PageResponse.from(recordPage, r -> enrichWithUser(r, userMap)));
    }

    /** 训练记录详情（含三层 exercises + sets） */
    @GetMapping("/{id}")
    public Result<AdminTrainingRecordDetailDto> detail(@PathVariable("id") Long id) {
        TrainingRecord record = trainingRecordService.findByIdForAdmin(id);
        AdminTrainingRecordDetailDto dto = AdminTrainingRecordDetailDto.from(record);
        User u = userRepository.findById(record.getUserId()).orElse(null);
        if (u != null) {
            dto.setUserUid(u.getUid());
            dto.setUserNickname(u.getNickname());
        }
        return Result.success(dto);
    }

    /**
     * 按筛选条件导出训练记录 CSV。
     *
     * <p>路径：{@code GET /api/admin/training-records/export}
     *
     * <p>列：训练 id / 用户 uid / 昵称 / 日期 / 开始 / 结束 / 时长(分) / 总容量(kg) / 总组数 /
     *       动作数 / 涉及肌群 / 备注 / 创建时间。
     *
     * <p>最多 {@link #MAX_EXPORT_SIZE} 条；超出请缩小筛选范围后再导。
     */
    @GetMapping("/export")
    public void exportCsv(HttpServletRequest request, HttpServletResponse response,
                          @RequestParam(value = "userId", required = false) Long userId,
                          @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                          @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) throws IOException {

        // 一次拉满，按 service 的内部排序（createdAt DESC 由 repository 保证）
        Page<TrainingRecord> recordPage = trainingRecordService.pageForAdmin(userId, start, end, 0, MAX_EXPORT_SIZE);
        List<TrainingRecord> records = recordPage.getContent();
        if (records.size() >= MAX_EXPORT_SIZE) {
            log.warn("[admin] 训练记录导出条数达到上限 {}（可能被截断），filters: userId={}, start={}, end={}",
                    MAX_EXPORT_SIZE, userId, start, end);
        }
        Map<Long, User> userMap = batchLoadUsers(records);

        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        String summary = String.format("rows=%d, userId=%s, start=%s, end=%s",
                records.size(), userId, start, end);
        auditLogService.logSuccess(request, AdminAuditAction.EXPORT_TRAINING_RECORDS,
                "TRAINING_RECORD", null, summary);
        log.info("导出训练记录 CSV, operator={}, {}", operator, summary);

        CsvHttpResponseUtil.write(response, "training_records",
                List.of("记录 id", "用户 uid", "用户昵称", "日期", "开始时间", "结束时间",
                        "时长(分)", "总容量(kg)", "总组数", "动作数", "涉及肌群", "备注", "创建时间"),
                records, r -> {
                    User u = userMap.get(r.getUserId());
                    return List.of(
                            String.valueOf(r.getId()),
                            u == null ? "" : nullToEmpty(u.getUid()),
                            u == null ? "" : nullToEmpty(u.getNickname()),
                            r.getDate() == null ? "" : r.getDate().format(DATE_FMT),
                            fmtIso(r.getStartedAt()),
                            fmtIso(r.getEndedAt()),
                            r.getDurationMin() == null ? "" : String.valueOf(r.getDurationMin()),
                            r.getTotalVolumeKg() == null ? "0" : String.format("%.1f", r.getTotalVolumeKg()),
                            r.getTotalSets() == null ? "0" : String.valueOf(r.getTotalSets()),
                            r.getExercises() == null ? "0" : String.valueOf(r.getExercises().size()),
                            nullToEmpty(r.getMuscleGroupsCsv()),
                            nullToEmpty(r.getNote()),
                            fmtIso(r.getCreatedAt())
                    );
                });
    }

    // ===== 内部 =====

    /** 批量查询 user，避免每条记录单查 DB */
    private Map<Long, User> batchLoadUsers(List<TrainingRecord> records) {
        Set<Long> userIds = records.stream()
                .map(TrainingRecord::getUserId)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return new HashMap<>();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private AdminTrainingRecordDto enrichWithUser(TrainingRecord r, Map<Long, User> userMap) {
        AdminTrainingRecordDto dto = AdminTrainingRecordDto.from(r);
        User u = userMap.get(r.getUserId());
        if (u != null) {
            dto.setUserUid(u.getUid());
            dto.setUserNickname(u.getNickname());
        }
        return dto;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String fmtIso(LocalDateTime t) {
        return t == null ? "" : t.format(ISO_FMT);
    }
}
