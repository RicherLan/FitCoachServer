package com.lanprojects.fitcoach.challenge;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.login.support.AuthSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
public class ChallengeController {
    private final ChallengeService service;
    private final AuthSupport auth;
    public record Start(@NotBlank String project, @Pattern(regexp="QUEST|ARENA") @NotNull String mode,
                        @Min(1) @Max(12) int level, @Min(0) @Max(2) int difficulty,
                        boolean publicScore, @NotBlank String model, @NotBlank String ruleVersion) {}
    public record Finish(@NotNull @Size(max=600) List<@NotNull @PositiveOrZero Long> events,
                         @NotNull @Size(max=3) List<@NotNull @Min(0) @Max(200) Integer> sets,
                         @Min(0) @Max(3600000) long elapsedMs, boolean completed, boolean interrupted) {}
    @GetMapping("/catalog") public Result<?> catalog() { return Result.success(service.catalog()); }
    @GetMapping("/me") public Result<?> me(@RequestHeader("Authorization") String token) {
        return Result.success(service.profile(auth.requireUserId(token)));
    }
    @PostMapping("/sessions") public Result<?> start(@RequestHeader("Authorization") String token,
                                                    @Valid @RequestBody Start request) {
        return Result.success(service.start(auth.requireUserId(token), request));
    }
    @PostMapping("/sessions/{id}/finish") public Result<?> finish(@RequestHeader("Authorization") String token,
             @PathVariable String id, @Valid @RequestBody Finish request) {
        return Result.success(service.finish(auth.requireUserId(token), id, request));
    }
    @PostMapping("/sessions/{id}/withdraw") public Result<?> withdraw(@RequestHeader("Authorization") String token,
                                                                     @PathVariable String id) {
        service.withdraw(auth.requireUserId(token), id); return Result.success();
    }
    @GetMapping("/board") public Result<?> board(@RequestParam(defaultValue="week") String period) {
        return Result.success(service.board(period));
    }
}
