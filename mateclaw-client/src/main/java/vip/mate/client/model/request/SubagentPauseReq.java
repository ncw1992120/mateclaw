package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 子 Agent 暂停请求
 */
@Data
public class SubagentPauseReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 父会话 ID */
    private String parentConversationId;

    /** 是否暂停 */
    private boolean paused;
}
