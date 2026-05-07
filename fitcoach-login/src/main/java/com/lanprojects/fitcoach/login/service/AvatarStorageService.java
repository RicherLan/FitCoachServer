package com.lanprojects.fitcoach.login.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 头像存储抽象接口。
 * <p>
 * 留口子的目的：本期实现 {@link LocalAvatarStorageService} 落本地磁盘 +
 * 通过 Spring 静态资源映射访问；后期切 OSS / S3 时只需新增一个实现并改 application.yml，
 * 业务层 {@link UserProfileService} 完全无感。
 */
public interface AvatarStorageService {

    /**
     * 保存一张头像并返回可被客户端访问的完整 URL。
     *
     * @param uid  用户唯一标识，用于日志 + 文件名前缀（避免碰撞）
     * @param file 上传的图片文件（已经过基础校验：非空 / contentType 合法 / size 不超）
     * @return 完整 URL，如 {@code http://host:8080/static/avatar/202506/uid_abc.jpg}
     */
    String saveAvatar(String uid, MultipartFile file);
}
