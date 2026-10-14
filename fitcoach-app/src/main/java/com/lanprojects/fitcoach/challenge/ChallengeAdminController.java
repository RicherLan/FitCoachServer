package com.lanprojects.fitcoach.challenge;

import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.model.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Existing /api/admin/** interceptor enforces administrator identity and write role. */
@RestController
@RequestMapping("/api/admin/challenges")
@RequiredArgsConstructor
public class ChallengeAdminController {
    private final ChallengeService service;
    public record Review(@NotBlank @Pattern(regexp="CONFIRMED|REVIEW|REVOKED") String state,
                         @NotBlank @Size(max=500) String reason) {}
    @PostMapping("/{id}/review")
    public Result<?> review(@PathVariable String id, @Valid @RequestBody Review review,
                            HttpServletRequest request) {
        service.review(id, review.state(), review.reason(),
                (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME));
        return Result.success();
    }
}
