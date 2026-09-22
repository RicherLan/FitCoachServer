package com.lanprojects.fitcoach.controller.exercise;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.exercise.service.ExerciseService;
import com.lanprojects.fitcoach.login.support.AuthSupport;
import com.lanprojects.fitcoach.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 每次开始训练从服务端检查上下架与会员，客户端缓存只用于展示。 */
@RestController
@RequiredArgsConstructor
public class ExerciseStartController {
    private final ExerciseService exercises;
    private final MembershipService memberships;
    private final AuthSupport auth;

    public record StartRequest(String exerciseKey) {}

    @PostMapping("/api/exercise/start")
    public Result<Void> start(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @RequestBody StartRequest request) {
        Long uid = auth.requireUserId(authorization);
        var exercise = exercises.findByKey(request.exerciseKey());
        if (!Boolean.TRUE.equals(exercise.getEnabled())) {
            throw new BusinessException(ResultCode.EXERCISE_DISABLED);
        }
        if (!Boolean.TRUE.equals(exercise.getIsFree())) { memberships.requireMembership(uid); }
        return Result.success();
    }
}
