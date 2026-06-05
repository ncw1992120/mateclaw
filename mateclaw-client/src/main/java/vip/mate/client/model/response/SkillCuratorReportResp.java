package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 技能策展器报告
 */
@Data
public class SkillCuratorReportResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 运行 ID */
    private String runId;

    /** 运行时间 */
    private LocalDateTime runAt;

    /** 是否试运行 */
    private boolean dryRun;

    /** 运行配置 */
    private CuratorConfigResp config;

    /** 扫描的技能总数 */
    private int scanned;

    /** 计划变更计数 */
    private CuratorCountsResp planned;

    /** 实际应用计数 */
    private CuratorCountsResp applied;

    /** 状态转换明细 */
    private List<CuratorTransitionRowResp> transitions;

    /** 被绑定关系阻止的技能列表 */
    private List<CuratorBlockedRowResp> blockedByBindings;

    /** 协调消息列表 */
    private List<String> reconciliations;
}
