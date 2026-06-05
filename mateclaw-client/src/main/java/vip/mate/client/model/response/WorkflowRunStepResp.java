package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流执行步骤响应
 */
@Data
public class WorkflowRunStepResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 运行 ID */
    private Long runId;

    /** 步骤索引 */
    private Integer stepIndex;

    /** 迭代索引 */
    private Integer iterationIndex;

    /** 步骤名称 */
    private String stepName;

    /** Agent ID */
    private Long agentId;

    /** 状态 */
    private String state;

    /** 输入引用 */
    private String inputRef;

    /** 输出引用 */
    private String outputRef;

    /** 输出摘要 */
    private String outputSummary;

    /** 输出内容类型 */
    private String outputContentType;

    /** 错误信息 */
    private String errorMessage;

    /** 执行耗时(ms) */
    private Long durationMs;

    /** 输入 Token 数 */
    private Integer tokenInput;

    /** 输出 Token 数 */
    private Integer tokenOutput;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
