package com.lanprojects.fitcoach.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * /api/auth/login/test 请求体 —— 内部测试账号登录。
 *
 * <p><b>仅 dev/staging 包客户端可见 + server 端 sys_config {@code test_login.enabled=true}
 * 才能成功</b>。生产环境强制关闭。
 *
 * <p>account 是测试账号短名（如 {@code test1}），server 端会拼上 {@code test_} 前缀
 * 作为实际 uid 去查 user 表，避免与真实 server-issued uid（UUID 去 -）冲突。
 *
 * <p>account 格式收紧到 {@code ^[a-zA-Z0-9_]{1,32}$}，防止有人用奇怪字符撞到生产 uid。
 */
@Data
public class TestLoginRequest {

    @NotBlank(message = "测试账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{1,32}$", message = "测试账号格式不合法")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 64, message = "密码长度不合法")
    private String password;
}
