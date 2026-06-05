package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 目标事件
 */
@Data
public class GoalEventResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 所属目标 ID */
    private Long goalId;

    /** 事件类型 */
    private String eventType;

    /** 关联的 assistant turn 消息 ID */
    private Long messageId;

    /** JSON 详情载荷 */
    private String detailJson;

    /** 创建时间 */
    private LocalDateTime createTime;
}
