package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 梦境状态信息
 */
@Data
public class DreamingStatusResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Dreaming 是否启用 */
    private boolean dreamingEnabled;

    /** Cron 表达式 */
    private String dreamingCron;

    /** 评分阈值 */
    private double scoreThreshold;

    /** 最小召回次数 */
    private int minRecallCount;

    /** 最小唯一查询数 */
    private int minUniqueQueries;

    /** 总召回记录数 */
    private long totalRecallEntries;

    /** 已提升数量 */
    private long promotedCount;

    /** 待处理候选数 */
    private long pendingCandidates;

    /** 上次执行时间 */
    private LocalDateTime lastRunTime;
}
