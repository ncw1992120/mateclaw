package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作流运行恢复请求
 */
@Data
public class WorkflowResumeReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 暂停令牌 */
    private String pauseToken;

    /** 恢复结果（approved / rejected / timeout / cancelled） */
    private String outcome;

    /** 负载数据 */
    private String payload;
}