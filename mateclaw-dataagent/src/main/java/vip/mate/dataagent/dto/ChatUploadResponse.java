package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 聊天附件上传响应
 */
@Data
public class ChatUploadResponse {

    /** 会话 ID */
    private String conversationId;

    /** 原始文件名 */
    private String fileName;

    /** 存储名称（时间戳_安全文件名） */
    private String storedName;

    /** 访问 URL 路径 */
    private String url;

    /** 服务端本地路径，用于后端工具消费 */
    private String path;

    /** 文件大小（字节） */
    private Long size;

    /** 文件内容类型 */
    private String contentType;
}
