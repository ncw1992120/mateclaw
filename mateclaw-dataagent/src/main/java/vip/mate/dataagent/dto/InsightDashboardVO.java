package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 洞察仪表盘视图对象
 */
@Data
public class InsightDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 仪表盘名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 仪表盘 Schema JSON（components 数组） */
    private String schemaJson;

    /** 状态：draft / published */
    private String status;

    /** AI 解读使用的 Agent ID */
    private Long agentId;

    /** 所属工作区 ID */
    private Long workspaceId;

    /** 所有者用户 ID */
    private Long ownerId;

    /** 修改人 */
    private String modifier;

    private String createTime;

    private String updateTime;
}
