package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话视图对象（包含 Agent 信息）
 */
@Data
public class ConversationResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String conversationId;
    private String title;
    private Long agentId;
    private String username;
    private Integer messageCount;
    private String lastMessage;
    private LocalDateTime lastActiveTime;
    private String streamStatus;
    private Long workspaceId;
    private String parentConversationId;
    private Integer pinned;
    private String modelProvider;
    private String modelName;
    private String progressLedger;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
    /**
     * VO 扩展字段
     */
    private String agentName;
    private String agentIcon;
    private String status;
    private String source;
}
