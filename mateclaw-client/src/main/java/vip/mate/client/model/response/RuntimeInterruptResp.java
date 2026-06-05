package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Agent 运行时子 Agent 中断结果
 */
@Data
public class RuntimeInterruptResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否已中断 */
    private boolean interrupted;
}
