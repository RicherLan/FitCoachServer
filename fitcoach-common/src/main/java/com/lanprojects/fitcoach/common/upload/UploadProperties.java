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

    /**
     * 新用户注册时分配的默认头像 URL（手机号 / 微信昵称头像缺失时兜底）。
     * <p>客户端的 normalizeAvatarUrl 已能处理 / 开头的相对路径，所以默认走相对路径，
     * 跨网络环境（模拟器 / 真机）一份配置都通用。后期若切 OSS / CDN，
     * 改成完整 https URL 即可，业务代码不动。
     * <p>留空时表示不下发默认头像（avatarUrl 入库 NULL，由客户端兜底渲染占位）。
     */
    private String defaultAvatarUrl = "/assets/default-avatar.svg";

    /** 头像专用配置 */
    private Avatar avatar = new Avatar();

    /** 意见反馈附件专用配置 */
    private Feedback feedback = new Feedback();

    /** App 版本安装包 + Mapping 文件上传配置 */
    private AppVersion appversion = new AppVersion();

    /** 训练动作自定义图标上传配置 */
    private TrainingExerciseIcon trainingExerciseIcon = new TrainingExerciseIcon();

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

    /**
     * 意见反馈附件配置。
     * <p>与 Avatar 隔离：反馈附件可能更多张、文件类型可能不一样，独立配置便于后续按需调整。
     */
    @Data
    public static class Feedback {
        /** 反馈附件存放子目录 */
        private String subDir = "feedback";

        /** 单个附件最大字节数；客户端会先压缩，服务端兜底校验 */
        private long maxSizeBytes = 1024 * 1024L;

        /** 单条反馈最多附件数；保护数据库 + 存储 */
        private int maxAttachmentCount = 5;

        /** 允许的 MIME 类型；本期仅支持图片，视频后续再加 */
        private List<String> allowedContentTypes = Arrays.asList("image/jpeg", "image/png", "image/webp");

        /** 反馈正文最大字符数 */
        private int maxContentLength = 1000;

        public List<String> getAllowedContentTypesView() {
            return Collections.unmodifiableList(allowedContentTypes);
        }
    }

    /**
     * App 版本安装包和 Mapping 文件上传配置。
     * <p>APK/IPA 文件通常 50-200MB，Mapping 文件通常几十 MB。
     */
    @Data
    public static class AppVersion {
        /** 安装包存放子目录 */
        private String subDir = "appversion";

        /** 安装包（APK/IPA）最大字节数 */
        private long packageMaxSizeBytes = 200 * 1024 * 1024L;  // 200MB

        /** Mapping 文件最大字节数 */
        private long mappingMaxSizeBytes = 100 * 1024 * 1024L;  // 100MB
    }

    /**
     * 训练动作自定义图标上传配置 — admin 在「训练动作库」编辑页上传 PNG/JPG/WebP 小图，
     * 客户端按 iconUrl 优先渲染，emoji 作为加载失败 / 离线兜底。
     *
     * <p>设计取舍：仅允许位图（JPEG/PNG/WebP），不开 SVG —— 因为 SVG 可内嵌脚本，
     * 客户端 FastImage 也不直接支持 SVG，避免运营误传引入额外渲染依赖。
     */
    @Data
    public static class TrainingExerciseIcon {
        /** 图标存放子目录（最终路径 baseDir/trainingexercise-icon/<exerciseKey>/uuid.ext） */
        private String subDir = "trainingexercise-icon";

        /** 单图最大字节数。图标是 64x64 / 128x128 小图，512KB 足够覆盖高质量 PNG */
        private long maxSizeBytes = 512 * 1024L;

        /** 允许的 MIME 类型（与客户端 FastImage 支持的位图格式对齐） */
        private List<String> allowedContentTypes = Arrays.asList("image/jpeg", "image/png", "image/webp");

        public List<String> getAllowedContentTypesView() {
            return Collections.unmodifiableList(allowedContentTypes);
        }
    }
}
