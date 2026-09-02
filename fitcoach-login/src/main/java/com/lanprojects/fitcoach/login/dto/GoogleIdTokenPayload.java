package com.lanprojects.fitcoach.login.dto;

/**
 * Google idToken 解析出的核心载荷（阶段 3B 波 2）。
 *
 * <p>Google 的 idToken 载荷字段丰富（sub / email / email_verified / name / picture / locale ...），
 * 本 record 只提取业务真正会用到的字段，其他字段调用方按需从 claims 二次解析。
 *
 * <p><b>字段含义</b>：
 * <ul>
 *   <li>{@code sub} —— Google 账号在当前 project 下的终身唯一 ID（同 project 跨 App 一致，
 *       跨 project 不复用）；</li>
 *   <li>{@code email} —— 用户邮箱，Google 每次登录都返回；</li>
 *   <li>{@code emailVerified} —— email 是否已验证；未验证的邮箱谨慎使用；</li>
 *   <li>{@code name} —— 显示名（一般是"given family"拼接）；</li>
 *   <li>{@code picture} —— 头像 URL，用户可能未设置或使用 Google 默认；</li>
 *   <li>{@code audience} —— idToken 的 aud，方便下游日志排查用了哪个 Client ID。</li>
 * </ul>
 */
public record GoogleIdTokenPayload(
        String sub,
        String email,
        Boolean emailVerified,
        String name,
        String picture,
        String audience) {

    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }

    public boolean hasName() {
        return name != null && !name.isBlank();
    }

    public boolean hasPicture() {
        return picture != null && !picture.isBlank();
    }
}
