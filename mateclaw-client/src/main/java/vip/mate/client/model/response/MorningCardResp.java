package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 梦境 Morning Card
 */
@Data
public class MorningCardResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 最新报告 ID */
    private Long reportId;

    /** 模式 (NIGHTLY/FOCUSED) */
    private String mode;

    /** 主题 */
    private String topic;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 提升数量 */
    private Integer promotedCount;

    /** 拒绝数量 */
    private Integer rejectedCount;

    /** LLM 解释 */
    private String llmReason;

    /** MEMORY.md diff */
    private String memoryDiff;
}
