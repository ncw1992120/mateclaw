package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Agent 运行时快照
 */
@Data
public class RuntimeSnapshotResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 摘要信息 */
    private RuntimeSummaryResp summary;

    /** 运行中的任务列表 */
    private List<RunCardResp> runs;

    /** 子 Agent 列表 */
    private List<SubagentCardResp> subagents;

    /** 时间戳 */
    private long timestamp;
}
