package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 中断对话结果
 */
@Data
public class ChatInterruptResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否中断 */
    private boolean interrupted;

    /** 是否成功入队 */
    private boolean queued;

    /** 当前队列大小 */
    private int queueSize;

    /** 原因 */
    private String reason;
}
