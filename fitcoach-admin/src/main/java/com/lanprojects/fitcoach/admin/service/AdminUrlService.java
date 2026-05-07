package com.lanprojects.fitcoach.admin.service;

import com.lanprojects.fitcoach.common.upload.UploadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * URL 拼接工具 — 把 DB 里存的相对路径（{@code /static/avatar/xxx.jpg}）转为绝对 URL。
 * <p>
 * 当前的 {@code upload.url-prefix} 默认是 {@code /static}（相对路径），
 * 客户端会用自己的 baseURL 拼接。但 admin 后台是独立 Web 项目，
 * 没有运行时 baseURL 注入概念 —— 因此返回给前端的图片 URL 必须是 server 的
 * {@code http(s)://host:port/static/...} 绝对路径。
 * <p>
 * 实现：
 * <ul>
 *   <li>如果 DB 存的已经是 {@code http(s)://} 开头 → 原样返回（CDN / OSS 场景）；</li>
 *   <li>否则当成相对路径，前端会自己拼前端 host —— 这里直接返回相对路径，
 *       让 admin 前端用 {@code VITE_API_BASE_URL} 拼接，与图片走同源关系。</li>
 * </ul>
 *
 * <p>之所以保留相对路径而不是在 server 主动拼绝对，是因为 server 不知道
 * 自己对外的真实域名（多机部署 + 反代很常见，从 request 取 Host 又可能被伪造），
 * 让前端自己拼是最稳的做法。前端只要保证 axios 实例 baseURL 指向 server 即可。
 */
@Service
@RequiredArgsConstructor
public class AdminUrlService {

    private final UploadProperties uploadProperties;

    /** 头像 URL：DB 已有完整 URL 直接返回；否则保留相对路径，由前端 baseURL 拼接 */
    public String resolve(String url) {
        if (url == null || url.isBlank()) {
            // 退而求其次给默认头像（也可能是相对路径 /assets/...）
            return uploadProperties.getDefaultAvatarUrl();
        }
        return url;
    }

    /** 批量解析（反馈附件用） */
    public List<String> resolveAll(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        return urls.stream().map(this::resolve).toList();
    }
}
