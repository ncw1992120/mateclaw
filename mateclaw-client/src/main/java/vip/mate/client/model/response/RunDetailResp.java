package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 运行详情响应
 * <p>
 * 对应服务端 RunDetail record，包含运行实例、步骤列表和活跃暂停记录
 */
@Data
public class RunDetailResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 运行实例 */
    private WorkflowRunResp run;

    /** 步骤列表 */
    private List<WorkflowRunStepResp> steps;

    /** 活跃暂停记录 */
    private WorkflowRunPauseResp activePause;
}
