package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 提供商偏好
 */
@Data
public class AgentProviderPreferenceResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long agentId;
    private String providerId;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
