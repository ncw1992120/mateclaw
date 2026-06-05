package vip.mate.client.model.response;

import lombok.Data;
import vip.mate.client.model.Workflow;

import java.io.Serializable;

/**
 * 工作流发布结果
 */
@Data
public class PublishOutcomeResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 更新后的工作流实体 */
    private Workflow workflow;

    /** 新创建的版本快照 */
    private WorkflowRevisionResp revision;
}
