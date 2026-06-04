package com.lanprojects.fitcoach.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * admin 后台手动创建用户的请求体。
 *
 * <p>language：admin 后台只用 zh-CN，错误码 message 直回中文。
 *
 * <p>创建后的 user 满足：
 * <ul>
 *   <li>{@code account} —— 可选，admin 指定则用指定值（需唯一），不传则由
 *       服务端 {@code AccountGenerator} 自动生成（8 位纯数字、唯一）；</li>
 *   <li>{@code passwordHash} —— BCrypt 哈希后写入；</li>
 *   <li>{@code loginType} = ACCOUNT、{@code registrationSource} = ADMIN_CREATED；</li>
 *   <li>{@code enabled} = true。</li>
 * </ul>
 */
@Data
public class CreateUserRequest {

    /** 昵称（必填，2-20 字符；与 C 端"设置昵称"规则保持一致） */
    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称长度需在 2-20 之间")
    private String nickname;

    /** 初始密码（必填，6-64 位；admin 端不做强度校验，便于运营起短密码做内部测试账号） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String password;

    /**
     * 自定义账号（可选）。
     * <p>不传或为空时由 {@code AccountGenerator} 自动生成 8 位纯数字账号。
     * <p>传值时需满足 1-16 位字母数字，且不能与已有账号重复。
     */
    @Size(min = 1, max = 16, message = "账号长度需在 1-16 之间")
    @Pattern(regexp = "^[A-Za-z0-9]*$", message = "账号只能包含字母和数字")
    private String account;
}
