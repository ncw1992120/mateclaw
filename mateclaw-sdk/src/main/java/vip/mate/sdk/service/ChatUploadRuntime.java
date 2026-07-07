package vip.mate.sdk.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 聊天附件上传运行时接口
 * <p>
 * 封装聊天场景下的附件上传与读取能力，宿主应用通过此接口
 * 可在编程层面直接操作聊天附件，无需依赖 mateclaw-server 的 HTTP 端点。
 */
public interface ChatUploadRuntime {

    /**
     * 上传聊天附件
     * <p>
     * 将文件存储到 {@code data/chat-uploads/{conversationId}/} 目录下，
     * 文件名格式为 {@code {timestamp}_{safeFilename}}，同时校验会话归属。
     *
     * @param conversationId 会话 ID
     * @param file           上传的文件
     * @return 上传结果
     */
    ChatUploadResult upload(String conversationId, MultipartFile file);

    /**
     * 读取聊天附件
     * <p>
     * 根据会话 ID 和存储名称读取已上传的附件资源，
     * 读取时验证路径安全性（防路径遍历攻击）。
     *
     * @param conversationId 会话 ID
     * @param storedName     存储名称
     * @return 附件资源，不存在时返回 null
     */
    Resource readUploadedFile(String conversationId, String storedName);

    /**
     * 聊天附件上传结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class ChatUploadResult {

        /** 会话 ID */
        private String conversationId;

        /** 原始文件名 */
        private String fileName;

        /** 存储名称（时间戳_安全文件名） */
        private String storedName;

        /** 访问 URL 路径 */
        private String url;

        /** 文件存储相对路径 */
        private String path;

        /** 文件大小（字节） */
        private Long size;

        /** 文件内容类型 */
        private String contentType;
    }
}
