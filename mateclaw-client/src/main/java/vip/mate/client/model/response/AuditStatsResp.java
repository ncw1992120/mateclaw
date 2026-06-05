package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 审计统计信息
 */
@Data
public class AuditStatsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 总审计数 */
    private long total;

    /** BLOCK决策数 */
    private long blocked;

    /** NEEDS_APPROVAL决策数 */
    private long needsApproval;

    /** ALLOW决策数 */
    private long allowed;
}
