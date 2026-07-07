package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建洞察仪表盘请求
 */
@Data
public class InsightDashboardCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仪表盘名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 仪表盘 Schema JSON（初始可为空模板） */
    private String schemaJson;

    /** AI 解读使用的 Agent ID（可选） */
    private Long agentId;
}
