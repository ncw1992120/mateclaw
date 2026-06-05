package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 触发器事件注入请求
 */
@Data
public class TriggerIngestReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 工作空间 ID */
    private long workspaceId;

    /** 模式类型 */
    private String patternType;

    /** 事件 ID */
    private String eventId;

    /** 发送者 ID */
    private String senderId;

    /** 事件数据 */
    private Map<String, Object> data;
}