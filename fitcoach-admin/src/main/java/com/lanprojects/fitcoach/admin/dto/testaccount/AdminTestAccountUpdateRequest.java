package com.lanprojects.fitcoach.admin.dto.testaccount;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑测试账号入参（PATCH 语义；null = 不动）。
 *
 * <ul>
 *   <li>{@code nickname} —— 可改昵称；传空字符串视为"清空"，传 null 视为"不动"；</li>
 *   <li>{@code enabled} —— 启用 / 禁用；禁用后客户端登录会被拒（USER_DISABLED）。</li>
 * </ul>
 *
 * <p>密码修改不在这里做（独立的 {@code POST /{id}/reset-password} 接口，避免误改）；
 * account 一旦创建禁止改名（不开放对应字段）。
 */
@Data
public class AdminTestAccountUpdateRequest {

    @Size(max = 100, message = "nickname 不超过 100 字")
    private String nickname;

    private Boolean enabled;
}
