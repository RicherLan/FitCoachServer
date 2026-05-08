package com.lanprojects.fitcoach.common.clientbus;

/**
 * 客户端通用轮询通道（GET /api/client/poll）的"贡献者" SPI。
 *
 * <p>各业务模块（fitcoach-log、未来的 version/config/message 等）通过实现本接口，向客户端
 * 单一轮询接口贡献自己模块的"待办字段"，避免每加一个能力都新增一个客户端轮询接口。
 *
 * <p>实现类只要声明为 Spring Bean（{@code @Component}），{@code ClientPollController}
 * 会通过 {@code List<ClientPollContribution>} 自动注入并在每次轮询时遍历调用。
 *
 * <p><b>语义约定</b>：
 * <ul>
 *   <li>{@link #key()} —— 在响应 JSON 中作为字段名，如 {@code "logTask"}、{@code "versionUpdate"}；
 *       同一进程内必须全局唯一，重复时启动会失败（由 controller 启动期校验）；</li>
 *   <li>{@link #resolve(String)} —— 返回当前 uid 对应的待办内容；
 *       <b>返回 null 表示"无内容"</b>，不会出现在响应 JSON 里（节省带宽 + 客户端用
 *       {@code response.xxx ?? null} 处理统一）；</li>
 *   <li>实现内部应做好异常隔离 —— 抛出异常会被 controller 捕获并 warn 日志，
 *       但不会让其他 contribution 失败；</li>
 *   <li><b>禁止做长耗时操作</b>（建议 &lt; 50ms）：客户端轮询周期通常 &lt; 60s，
 *       contribution 串行执行，单个慢会拖慢整个轮询。</li>
 * </ul>
 */
public interface ClientPollContribution {

    /**
     * 在响应 JSON 中的字段名。同一进程内必须唯一。
     * <p>命名建议：camelCase 名词，避免动词（如 "logTask" 而非 "fetchLogTask"）。
     */
    String key();

    /**
     * 解析当前 uid 的待办内容。
     *
     * @param uid 当前调用方用户 uid（已通过鉴权）
     * @return 待办对象（任意可序列化为 JSON 的 POJO）；返回 null 表示无内容
     */
    Object resolve(String uid);
}
