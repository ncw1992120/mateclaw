package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识库统计信息
 */
@Data
public class KbStatsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 页面数量 */
    private int pageCount;

    /** 已增强页面数量 */
    private long enrichedPageCount;

    /** 失败任务数量 */
    private int failedJobCount;

    /** 运行中任务数量 */
    private int runningJobCount;

    /** 嵌入漂移信息 */
    private EmbeddingDriftResp embeddingDrift;
}
