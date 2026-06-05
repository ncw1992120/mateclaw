package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 触发器事件响应
 */
@Data
public class TriggerEventResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long triggerId;
    private String dedupKey;
    private LocalDateTime receivedAt;
    private LocalDateTime expiresAt;
}
