package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 子 Agent 暂停结果
 */
@Data
public class SubagentPauseResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前的暂停状态 */
    private boolean paused;
}
