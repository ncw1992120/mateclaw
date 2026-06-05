package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 触发器事件注入结果
 */
@Data
public class TriggerIngestResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 触发器 ID */
    private long triggerId;

    /** 是否触发 */
    private boolean fired;

    /** 未触发原因 */
    private String droppedReason;
}