package com.lanprojects.fitcoach.admin.dto.testaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置测试账号密码入参。
 *
 * <p>独立于 {@link AdminTestAccountUpdateRequest}：
 * <ul>
 *   <li>避免误把昵称变更附带改密；</li>
 *   <li>audit log 单独记一条 {@code RESET_TEST_ACCOUNT_PASSWORD}，回查清晰。</li>
 * </ul>
 */
@Data
public class AdminTestAccountResetPasswordRequest {

    @NotBlank(message = "password 不能为空")
    @Size(min = 6, max = 64, message = "password 长度需在 6-64 之间")
    private String password;
}
