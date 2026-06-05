package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Hook 实体响应
 */
@Data
public class HookResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private Boolean enabled;
    /** 事件类型，如 agent:end、tool:* */
    private String eventType;
    /** JSON 过滤表达式 */
    private String matchExpression;
    /** HookAction.Kind#name() */
    private String actionKind;
    /** JSON，因 actionKind 不同内容不同 */
    private String actionConfig;
    private Integer rateLimitPerMin;
    private Integer timeoutMs;
    /** db 或 file */
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
