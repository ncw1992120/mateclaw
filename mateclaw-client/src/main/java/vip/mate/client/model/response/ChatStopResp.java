package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 停止对话结果
 */
@Data
public class ChatStopResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否成功停止 */
    private boolean stopped;

    /** 清理的幽灵审批数量 */
    private int ghostPendingsCleared;

    /** 重写的消息数量 */
    private int messagesRewritten;
}
