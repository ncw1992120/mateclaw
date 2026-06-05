package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Agent 运行时摘要信息
 */
@Data
public class RuntimeSummaryResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 运行中数量 */
    private int running;

    /** 卡住数量 */
    private int stuck;

    /** 孤儿数量 */
    private int orphan;

    /** 队列中数量 */
    private int queued;

    /** 活跃子 Agent 数量 */
    private int subagentsActive;
}
