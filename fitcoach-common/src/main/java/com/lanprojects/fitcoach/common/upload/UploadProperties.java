package com.lanprojects.fitcoach.common.upload;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 文件上传配置 — 对应 application.yml 的 {@code upload.*} 节点。
 * <p>
 * 单独抽一个 properties 类的好处：
 * <ul>
 *   <li>所有上传相关的服务都从这里取值，不重复散写 {@code @Value}；</li>
 *   <li>后期切 OSS / S3 时只需新增一个 storage 配置块，业务代码改动最小。</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    /** 上传文件存盘根目录（绝对/相对均可，相对路径以启动目录为基准） */
    private String baseDir = "./uploads";

    /** 静态资源 URL 前缀（如 {@code http://host:8080/static}），返回给客户端拼成完整 URL */
    private String urlPrefix = "http://localhost:8080/static";

    /** 头像专用配置 */
    private Avatar avatar = new Avatar();

    @Data
    public static class Avatar {
        /** 头像存放的相对子目录（拼到 baseDir 之后） */
        private String subDir = "avatar";

        /** 头像最大字节数；超过直接 422，避免恶意大图占满磁盘 */
        private long maxSizeBytes = 2 * 1024 * 1024L;

        /**
         * 允许的 MIME 类型；逗号分隔字符串由 Spring 自动转 {@code List<String>}。
         * <p>注意：客户端传上来的 contentType 可被伪造，服务端还需要做 magic number 兜底校验。
         */
        private List<String> allowedContentTypes = Arrays.asList("image/jpeg", "image/png", "image/webp");

        /** 让外部读到不可变 list，防止意外修改。 */
        public List<String> getAllowedContentTypesView() {
            return Collections.unmodifiableList(allowedContentTypes);
        }
    }
}
