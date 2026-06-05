package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道实体
 */
@Data
public class Channel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String channelType;
    private Long agentId;
    private String botPrefix;
    private String configJson;
    private String identityJson;
    private Boolean enabled;
    private String description;
    private Long workspaceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
