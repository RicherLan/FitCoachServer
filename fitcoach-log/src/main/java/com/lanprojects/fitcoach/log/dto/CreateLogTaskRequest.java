package com.lanprojects.fitcoach.log.dto;

import lombok.Data;

/**
 * admin 创建日志拉取任务的请求体。
 *
 * <p>{@code uid} 必填；{@code remark} / {@code recentHours} 可选。
 * <ul>
 *   <li>{@code recentHours} 客户端会按 createdAt &gt;= now - recentHours 过滤上传范围；
 *       为 null 表示拉所有可用日志（受客户端 maxFiles=10 限制兜底）。</li>
 * </ul>
 */
@Data
public class CreateLogTaskRequest {
    /** 目标用户 uid（来自 User.uid） */
    private String uid;

    /** 任务备注（admin 填写，下载时给 admin 回顾用） */
    private String remark;

    /** 拉取最近 N 小时内的日志；null 表示全量 */
    private Integer recentHours;
}
