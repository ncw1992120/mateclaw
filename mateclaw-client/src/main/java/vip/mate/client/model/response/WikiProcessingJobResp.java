package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Wiki 处理任务
 */
@Data
public class WikiProcessingJobResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 知识库ID */
    private Long kbId;

    /** 原始材料ID */
    private Long rawId;

    /** 任务类型 */
    private String jobType;

    /** 当前阶段 */
    private String stage;

    /** 状态 */
    private String status;

    /** 主模型ID */
    private Long primaryModelId;

    /** 当前模型ID */
    private Long currentModelId;

    /** 降级链(JSON) */
    private String fallbackChainJson;

    /** 重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetries;

    /** 错误码 */
    private String errorCode;

    /** 错误信息 */
    private String errorMessage;

    /** 恢复起始阶段 */
    private String resumeFromStage;

    /** 通用元数据JSON */
    private String metaJson;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
