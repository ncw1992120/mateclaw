package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 子 Agent 中断结果
 */
@Data
public class SubagentInterruptResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否成功中断 */
    private boolean interrupted;
}
