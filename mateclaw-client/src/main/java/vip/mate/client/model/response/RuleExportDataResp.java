package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 安全规则导出数据
 */
@Data
public class RuleExportDataResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 导出模式标识 */
    private String schema;

    /** 导出时间 */
    private String exportedAt;

    /** 规则数量 */
    private int count;

    /** 规则列表 */
    private List<GuardRuleResp> rules;
}
