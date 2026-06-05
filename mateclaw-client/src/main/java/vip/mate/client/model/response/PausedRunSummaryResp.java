package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 暂停运行摘要响应
 * <p>
 * 对应服务端 PausedRunSummary record，包含运行实例和暂停记录
 */
@Data
public class PausedRunSummaryResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 运行实例 */
    private WorkflowRunResp run;

    /** 暂停记录 */
    private WorkflowRunPauseResp pause;
}
