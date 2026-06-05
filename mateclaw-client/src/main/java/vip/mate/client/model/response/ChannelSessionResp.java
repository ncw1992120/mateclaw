package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道会话响应
 */
@Data
public class ChannelSessionResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String conversationId;
    private String channelType;
    private String targetId;
    private String senderId;
    private String senderName;
    private Long channelId;
    private LocalDateTime lastActiveTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
