package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 策展器状态转换行
 */
@Data
public class CuratorTransitionRowResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 技能 ID */
    private Long skillId;

    /** 技能名称 */
    private String name;

    /** 原状态 */
    private String from;

    /** 目标状态 */
    private String to;

    /** 空闲天数 */
    private long daysIdle;
}
