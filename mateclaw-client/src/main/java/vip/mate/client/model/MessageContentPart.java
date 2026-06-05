package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 消息内容部分
 */
@Data
public class MessageContentPart implements Serializable {
    private static final long serialVersionUID = 1L;

    private String type;
    private String text;
    private String fileUrl;
    private String fileName;
    private String storedName;
    private String contentType;
    private Long fileSize;
    private String path;
    private String mediaId;
}
