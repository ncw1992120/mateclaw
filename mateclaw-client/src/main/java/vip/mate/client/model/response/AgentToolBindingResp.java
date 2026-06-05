package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 工具绑定
 */
@Data
public class AgentToolBindingResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long agentId;
    private String toolName;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
