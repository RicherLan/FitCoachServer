package com.lanprojects.fitcoach.common.client;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;

import java.util.EnumSet;
import java.util.Set;

/**
 * Flavor × LoginMethod 白名单策略中心 —— 阶段 3A 服务端根源防线。
 *
 * <p>AuthController 各登录端点入口调用 {@link #ensureAllowed(LoginMethod)}，
 * 该方法从 {@link ClientContext#appFlavor()} 读取当前请求的 flavor（由
 * {@link ClientInfoInterceptor} 从 {@code X-App-Flavor} 请求头解析），
 * 并与内置白名单比对；不合规立即抛 {@link ResultCode#LOGIN_METHOD_NOT_ALLOWED}。
 *
 * <p>这样即使客户端 UI 层的 flavor 过滤被绕过（如攻击者用 curl 直接构造请求），
 * 服务端也能兜底拒绝"CN 包发 Google 登录 / GLOBAL 包发微信登录"这类跨 flavor 请求。
 *
 * <h3>与 RN 端 flavorProfile 的对齐关系</h3>
 * <table border="1" cellpadding="4">
 *   <tr><th>flavor</th><th>RN loginMethods（小写）</th><th>本类白名单（大写）</th></tr>
 *   <tr><td>CN</td><td>['wechat','apple','phone']</td><td>{@link #CN_METHODS} + ACCOUNT</td></tr>
 *   <tr><td>GLOBAL</td><td>['google','apple','email']</td><td>{@link #GLOBAL_METHODS} + ACCOUNT</td></tr>
 * </table>
 *
 * <p><b>注意</b>：RN {@code CN_PROFILE.loginMethods} 里之所以列了 apple，
 * 是因为"CN iOS 包在 App Store 上架也需要提供 Apple 登录以满足审核要求"。
 * 但这一点的开关权由 client 侧 Platform.OS 二次过滤控制，服务端本层不需要为
 * "CN + APPLE" 单独放行 —— 待阶段 3B 实际接入 Apple 登录时再评估是否需要调整此白名单。
 *
 * <h3>ACCOUNT 是全 flavor 通用的例外</h3>
 * account（用户号）+ 密码 是 user 的内在唯一凭证，任何注册方式首次登录后 server 都会
 * 自动生成 account。因此账号密码登录对所有 flavor 一律放行，不进白名单判断。
 *
 * <h3>flavor == null 的处理策略</h3>
 * 老客户端（升级前不带 X-App-Flavor 头）/ admin 后台 / Postman 调试均可能不带 flavor。
 * 此时保守放行，避免误伤存量调用。未来客户端全量升级、灰度覆盖完成后，
 * 可考虑改为"缺失即拒绝"以收紧安全边界。
 */
public final class FlavorLoginPolicy {

    /**
     * CN flavor 允许的登录方式白名单（不含全 flavor 通用的 ACCOUNT）。
     * <p>与 RN {@code flavorProfile.ts#CN_PROFILE.loginMethods} 保持同步；
     * 增删项时两端必须同时改动。
     */
    private static final Set<LoginMethod> CN_METHODS = EnumSet.of(
            LoginMethod.WECHAT,
            LoginMethod.PHONE);

    /**
     * GLOBAL flavor 允许的登录方式白名单（不含全 flavor 通用的 ACCOUNT）。
     * <p>与 RN {@code flavorProfile.ts#GLOBAL_PROFILE.loginMethods} 保持同步；
     * 增删项时两端必须同时改动。
     */
    private static final Set<LoginMethod> GLOBAL_METHODS = EnumSet.of(
            LoginMethod.GOOGLE,
            LoginMethod.APPLE,
            LoginMethod.EMAIL);

    private FlavorLoginPolicy() {
        // 工具类：禁止实例化
    }

    /**
     * 判断给定 flavor 是否允许指定的登录方式。
     * <ul>
     *   <li>flavor == null → true（老客户端/admin/Postman 兜底放行）</li>
     *   <li>method == {@link LoginMethod#ACCOUNT} → true（用户号+密码是全 flavor 通用凭证）</li>
     *   <li>其他 → 按 flavor 对应白名单判断</li>
     * </ul>
     */
    public static boolean isAllowed(AppFlavor flavor, LoginMethod method) {
        if (flavor == null) {
            return true;
        }
        if (method == LoginMethod.ACCOUNT) {
            return true;
        }
        return switch (flavor) {
            case CN -> CN_METHODS.contains(method);
            case GLOBAL -> GLOBAL_METHODS.contains(method);
        };
    }

    /**
     * 静态守卫 —— AuthController 各登录端点入口第一行调用；不合规立即中断当前请求。
     * <p>flavor 从 {@link ClientContext#appFlavor()} 读取（由 ClientInfoInterceptor 预置）。
     */
    public static void ensureAllowed(LoginMethod method) {
        AppFlavor flavor = ClientContext.appFlavor();
        if (!isAllowed(flavor, method)) {
            throw new BusinessException(ResultCode.LOGIN_METHOD_NOT_ALLOWED);
        }
    }
}
