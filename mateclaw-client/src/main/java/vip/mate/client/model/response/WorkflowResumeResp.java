package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作流运行恢复结果
 */
@Data
public class WorkflowResumeResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 结果类型 */
    private String kind;

    /** 运行 ID */
    private Long runId;

    /** 错误信息 */
    private String errorMessage;
}