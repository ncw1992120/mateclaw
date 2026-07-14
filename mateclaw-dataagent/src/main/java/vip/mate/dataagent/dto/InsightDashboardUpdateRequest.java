package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新洞察仪表盘请求
 */
@Data
public class InsightDashboardUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仪表盘名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 仪表盘 Schema JSON（components 数组） */
    private String schemaJson;

    /** AI 分析报告内容（HTML 格式） */
    private String reportContent;

    /** 状态：draft / published */
    private String status;

    /** AI 解读使用的 Agent ID */
    private Long agentId;

    /** 负责人名称 */
    private String ownerName;
}
