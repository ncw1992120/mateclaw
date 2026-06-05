package vip.mate.client.model.response;

import lombok.Data;
import vip.mate.client.model.MessageContentPart;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息实体
 */
@Data
public class MessageResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String conversationId;
    private String role;
    private String content;
    private String toolName;
    private String status;
    private Object metadata;
    private Integer promptTokens;
    private Integer completionTokens;
    private String runtimeModel;
    private String runtimeProvider;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<MessageContentPart> contentParts;
}
