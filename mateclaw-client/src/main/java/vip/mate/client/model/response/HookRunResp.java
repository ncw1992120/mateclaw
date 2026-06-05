package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Hook 执行记录响应
 */
@Data
public class HookRunResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long hookId;
    /** 触发事件类型 */
    private String eventType;
    /** HookResult.Status#name() */
    private String status;
    private Integer durationMs;
    /** 错误/结果消息 */
    private String message;
    private LocalDateTime createdAt;
}
