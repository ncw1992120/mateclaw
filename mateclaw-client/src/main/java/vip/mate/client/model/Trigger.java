package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 触发器
 */
@Data
public class Trigger implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workspaceId;
    private String name;
    private String patternType;
    private String patternJson;
    private String targetType;
    private Long targetId;
    private String payloadTemplate;
    private Integer rateLimitPerMin;
    private Integer dedupWindowSecs;
    private Boolean botSelfFilter;
    private Boolean enabled;
    private Long fireCount;
    private Long maxFires;
    private LocalDateTime lastFiredAt;
    private String lastError;
    private LocalDateTime lastDispatchedAt;
    private Long patternVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
