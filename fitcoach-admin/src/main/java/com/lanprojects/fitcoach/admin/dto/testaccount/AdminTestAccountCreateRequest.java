package com.lanprojects.fitcoach.admin.dto.testaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建测试账号入参。
 *
 * <p>语义：service 会拼出 uid = {@code test_<account>}，撞名直接拒。
 *
 * <ul>
 *   <li>{@code account} —— 短账号名，正则 {@code ^[a-zA-Z0-9_]{1,32}$}。
 *       该值是客户端摇一摇面板里输入的"账号"，也是 server seed 的 {@code test1/test2/test3} 同一字段；</li>
 *   <li>{@code password} —— 明文密码，server 端 BCrypt 加密后入 {@code user.password_hash}；
 *       长度 6-64，与 admin 修改密码同口径；</li>
 *   <li>{@code nickname} —— 可选；不传默认"测试账号 &lt;account&gt;"。</li>
 * </ul>
 */
@Data
public class AdminTestAccountCreateRequest {

    @NotBlank(message = "account 不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{1,32}$",
            message = "account 仅支持英文/数字/下划线，长度 1-32")
    private String account;

    @NotBlank(message = "password 不能为空")
    @Size(min = 6, max = 64, message = "password 长度需在 6-64 之间")
    private String password;

    /** 可选；不传时 service 自动生成 "测试账号 <account>" */
    @Size(max = 100, message = "nickname 不超过 100 字")
    private String nickname;
}
