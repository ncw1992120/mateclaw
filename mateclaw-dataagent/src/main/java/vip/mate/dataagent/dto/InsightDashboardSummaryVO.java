package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 洞察仪表盘列表摘要。
 * <p>
 * 列表页只需要卡片所需的元信息与缩略图类型；刻意不携带 {@code schemaJson} 与
 * {@code reportContent} 两个大字段，避免列表首屏为整页 Schema 付出序列化与传输成本。
 * 详情读取仍走 {@code InsightDashboardVO}（GET /{id}），本对象不替代详情契约。
 */
@Data
public class InsightDashboardSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 仪表盘名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 状态：draft / published */
    private String status;

    /** AI 解读使用的 Agent ID */
    private Long agentId;

    /** 所属工作区 ID */
    private Long workspaceId;

    /** 所有者用户 ID */
    private Long ownerId;

    /** 负责人名称 */
    private String ownerName;

    /** 可见性：private / workspace / template / official */
    private String visibility;

    /** 模板元信息 JSON */
    private String templateMeta;

    /** 修改人 */
    private String modifier;

    private String createTime;

    private String updateTime;

    /**
     * 列表缩略图类型：由 Schema 轻量推导（bar / line / area / donut / funnel /
     * dual-line / bar-alert / kpi-grid / empty），用于卡片图形与空态展示。
     */
    private String chartKind;
}
