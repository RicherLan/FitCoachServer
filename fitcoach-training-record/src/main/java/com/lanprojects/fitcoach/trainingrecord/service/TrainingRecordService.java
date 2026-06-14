package com.lanprojects.fitcoach.trainingrecord.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.trainingrecord.dto.TrainingRecordRequest;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingExercise;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecord;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecordExercise;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecordSet;
import com.lanprojects.fitcoach.trainingrecord.repository.TrainingExerciseRepository;
import com.lanprojects.fitcoach.trainingrecord.repository.TrainingRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 训练记录核心服务。
 *
 * <p><b>核心职责</b>：
 * <ol>
 *   <li><b>幂等创建/更新</b>（{@link #createOrReplace}）：按 (userId, clientId) 查找，
 *       命中走 update 路径（删旧 exercises 全套，按新请求重建），未命中走 create 路径；</li>
 *   <li><b>自动派生字段</b>（{@link #recomputeAggregates}）：在保存前从 exercises/sets
 *       计算 totalVolumeKg / totalSets / muscleGroupsCsv，无需客户端传；</li>
 *   <li><b>权限边界</b>：所有读写都要带 userId，避免 IDOR；用 {@code findByIdAndUserId}；</li>
 *   <li><b>软引用快照</b>：写入 exercise 条目时从 training_exercise 表拷贝 name/muscleGroup/equipment/emoji
 *       到本表，原动作下架不影响历史记录展示。</li>
 * </ol>
 *
 * <p><b>事务边界</b>：写方法均 {@code @Transactional}，保证父子表写入的原子性。
 * 读方法走 {@code @Transactional(readOnly = true)}，让 JPA 知道这是只读事务可省 dirty check。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingRecordService {

    private final TrainingRecordRepository trainingRecordRepository;
    private final TrainingExerciseRepository trainingExerciseRepository;

    // ====== 查询 ======

    /** 用户列表分页（按 date desc） */
    @Transactional(readOnly = true)
    public Page<TrainingRecord> pageByUser(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100); // 1~100 兜底
        return trainingRecordRepository.pageByUserOrderByDateDesc(
                userId, PageRequest.of(Math.max(page, 0), safeSize));
    }

    /** 按日期范围查询用户的训练记录（详情/月概要用） */
    @Transactional(readOnly = true)
    public List<TrainingRecord> listByUserBetween(Long userId, LocalDate start, LocalDate end) {
        return trainingRecordRepository.listByUserBetween(userId, start, end);
    }

    /** 详情：按 (id, userId) 查询，找不到统一抛 NOT_FOUND（防 IDOR + 信息泄漏） */
    @Transactional(readOnly = true)
    public TrainingRecord findByIdForUser(Long id, Long userId) {
        return trainingRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.TRAINING_RECORD_NOT_FOUND));
    }

    /** 月度训练次数 */
    @Transactional(readOnly = true)
    public long countByUserBetween(Long userId, LocalDate start, LocalDate end) {
        return trainingRecordRepository.countByUserIdAndDateBetween(userId, start, end);
    }

    /**
     * Admin 端：按用户 id（可选）+ 日期范围（可选）分页查训练记录。
     * <p>用户 id 和日期范围同时为 null 时返回全量分页。
     */
    @Transactional(readOnly = true)
    public Page<TrainingRecord> pageForAdmin(Long userIdFilter, LocalDate start, LocalDate end,
                                             int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);
        if (userIdFilter != null) {
            return trainingRecordRepository.pageByUserForAdmin(userIdFilter, pageable);
        }
        if (start != null && end != null) {
            return trainingRecordRepository.pageByDateRangeForAdmin(start, end, pageable);
        }
        return trainingRecordRepository.pageAllForAdmin(pageable);
    }

    /** Admin 端详情（不带 userId 过滤） */
    @Transactional(readOnly = true)
    public TrainingRecord findByIdForAdmin(Long id) {
        return trainingRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.TRAINING_RECORD_NOT_FOUND));
    }

    // ====== 写入 ======

    /**
     * 创建或更新训练记录（按 clientId 幂等）。
     *
     * <p><b>命中规则</b>：
     * <ul>
     *   <li>同 (userId, clientId) 已存在 → 更新现有记录（exercises 走"删后插"）；</li>
     *   <li>不存在 → 创建新记录。</li>
     * </ul>
     *
     * <p><b>更新策略：删后插</b>（不是 diff）：清空 record.exercises，按 req.exercises 顺序重新
     * 创建子条目和组数。这样：实现简单；id 会变但客户端用 clientId 对账不依赖 id；
     * 不会出现"部分更新 + 部分残留"的脏状态。
     */
    @Transactional
    public TrainingRecord createOrReplace(Long userId, TrainingRecordRequest req) {
        if (req.getClientId() == null || req.getClientId().isBlank()) {
            throw new BusinessException(ResultCode.TRAINING_RECORD_CLIENT_ID_REQUIRED);
        }

        TrainingRecord record = trainingRecordRepository
                .findByUserIdAndClientId(userId, req.getClientId())
                .orElseGet(() -> {
                    TrainingRecord fresh = new TrainingRecord();
                    fresh.setUserId(userId);
                    fresh.setClientId(req.getClientId());
                    return fresh;
                });

        boolean isUpdate = record.getId() != null;

        // 顶层字段
        record.setDate(req.getDate());
        record.setStartedAt(req.getStartedAt());
        record.setEndedAt(req.getEndedAt());
        record.setDurationMin(resolveDuration(req.getDurationMin(), req.getStartedAt(), req.getEndedAt()));
        record.setNote(req.getNote());

        // 子表：删后插（保留 orphanRemoval 处理旧 exercises 删除）
        record.getExercises().clear();

        // 预拉一次所有 training_exercise 减少 N+1 — 把 req 中出现的 key 一次性查出来
        Map<String, TrainingExercise> exerciseByKey = preloadExercisesByKeys(req.getExercises(), userId);

        for (int i = 0; i < req.getExercises().size(); i++) {
            TrainingRecordRequest.ExerciseItem item = req.getExercises().get(i);
            TrainingExercise meta = exerciseByKey.get(item.getExerciseKey());
            if (meta == null) {
                throw new BusinessException(ResultCode.TRAINING_RECORD_EXERCISE_NOT_FOUND);
            }
            TrainingRecordExercise ex = new TrainingRecordExercise();
            ex.setPosition(i);
            ex.setExerciseKey(meta.getExerciseKey());
            ex.setExerciseName(meta.getDisplayName());   // 快照
            ex.setMuscleGroup(meta.getMuscleGroup());    // 快照
            ex.setEquipment(meta.getEquipment());        // 快照
            ex.setEmoji(meta.getEmoji());                 // 快照
            ex.setIconUrl(meta.getIconUrl());             // 快照 (v2 自定义图标)

            // 写入 sets，setIndex 从 1 起重排
            for (int s = 0; s < item.getSets().size(); s++) {
                TrainingRecordRequest.SetItem si = item.getSets().get(s);
                TrainingRecordSet set = new TrainingRecordSet();
                set.setSetIndex(s + 1);
                set.setWeightKg(si.getWeightKg());
                set.setReps(si.getReps());
                set.setIsWarmup(Boolean.TRUE.equals(si.getIsWarmup()));
                ex.addSet(set);
            }
            record.addExercise(ex);
        }

        // 派生字段
        recomputeAggregates(record);

        TrainingRecord saved = trainingRecordRepository.save(record);
        log.info("[training-record] {} id={} userId={} clientId={} date={} exercises={} sets={} volume={}kg",
                isUpdate ? "更新" : "创建",
                saved.getId(), userId, req.getClientId(), saved.getDate(),
                saved.getExercises().size(), saved.getTotalSets(), saved.getTotalVolumeKg());
        return saved;
    }

    /** 删除训练记录（硬删，带权限校验） */
    @Transactional
    public void delete(Long id, Long userId) {
        TrainingRecord existing = findByIdForUser(id, userId);
        trainingRecordRepository.delete(existing);
        log.info("[training-record] 删除 id={} userId={} clientId={}",
                id, userId, existing.getClientId());
    }

    // ====== 内部工具 ======

    /**
     * 预加载所有用到的 training_exercise（一次 IN 查询防 N+1）。
     * <p>同时查"内置 + 当前用户自定义"两张白名单的合集。
     */
    private Map<String, TrainingExercise> preloadExercisesByKeys(
            List<TrainingRecordRequest.ExerciseItem> items, Long userId) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<String, TrainingExercise> out = new HashMap<>();
        // 用 listVisibleEnabledForUser 已经能合并 builtin + 自定义；我们只筛 key 命中的即可
        List<TrainingExercise> all = trainingExerciseRepository.findVisibleEnabledForUser(userId);
        Map<String, TrainingExercise> indexed = new HashMap<>();
        for (TrainingExercise t : all) {
            indexed.put(t.getExerciseKey(), t);
        }
        for (TrainingRecordRequest.ExerciseItem it : items) {
            TrainingExercise hit = indexed.get(it.getExerciseKey());
            if (hit != null) {
                out.put(it.getExerciseKey(), hit);
            }
        }
        return out;
    }

    /**
     * 重算派生字段：totalVolumeKg / totalSets / muscleGroupsCsv。
     *
     * <p>计算规则：
     * <ul>
     *   <li><b>totalVolumeKg</b> = Σ (weightKg × reps)，<b>含</b>热身组（暂不区分，将来如要区分加 isWarmup 过滤）；</li>
     *   <li><b>totalSets</b> = 所有 exercises.sets 的累加；</li>
     *   <li><b>muscleGroupsCsv</b> = exercises 的 muscleGroup 去重并按字典序拼接。</li>
     * </ul>
     */
    private void recomputeAggregates(TrainingRecord record) {
        double volume = 0.0;
        int totalSets = 0;
        TreeSet<String> muscleGroups = new TreeSet<>();

        for (TrainingRecordExercise ex : record.getExercises()) {
            if (ex.getMuscleGroup() != null && !ex.getMuscleGroup().isBlank()) {
                muscleGroups.add(ex.getMuscleGroup());
            }
            for (TrainingRecordSet s : ex.getSets()) {
                totalSets++;
                Double w = s.getWeightKg();
                Integer r = s.getReps();
                if (w != null && r != null) {
                    volume += w * r;
                }
            }
        }

        record.setTotalVolumeKg(round1(volume));
        record.setTotalSets(totalSets);
        record.setMuscleGroupsCsv(String.join(",", muscleGroups));
    }

    /**
     * 计算训练时长：优先用客户端传值，否则从 startedAt/endedAt 算分钟（向下取整）。
     */
    private Integer resolveDuration(Integer explicit, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (explicit != null) {
            return explicit;
        }
        if (start != null && end != null && end.isAfter(start)) {
            return (int) Duration.between(start, end).toMinutes();
        }
        return null;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
