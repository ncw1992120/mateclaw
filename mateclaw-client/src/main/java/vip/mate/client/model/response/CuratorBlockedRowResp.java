package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 策展器被绑定阻止行
 */
@Data
public class CuratorBlockedRowResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 技能 ID */
    private Long skillId;

    /** 技能名称 */
    private String name;

    /** 绑定了该技能的 Agent ID 列表 */
    private List<Long> agentIds;

    /** 空闲天数 */
    private long daysIdle;
}
