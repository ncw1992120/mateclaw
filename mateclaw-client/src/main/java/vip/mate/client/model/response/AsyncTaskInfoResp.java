package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 异步任务信息响应
 */
@Data
public class AsyncTaskInfoResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    /** 对外公开的任务 ID（UUID） */
    private String taskId;
    /** 如 video_generation / image_generation */
    private String taskType;
    /** pending / running / succeeded / failed */
    private String status;
    /** 关联会话 ID */
    private String conversationId;
    /** 关联消息 ID */
    private Long messageId;
    /** 处理该任务的 provider */
    private String providerName;
    /** provider 返回的外部任务 ID */
    private String providerTaskId;
    /** 序列化请求参数（JSON） */
    private String requestJson;
    /** 序列化结果（JSON） */
    private String resultJson;
    private String errorMessage;
    /** 进度 0-100 */
    private Integer progress;
    /** 创建人 */
    private String createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
