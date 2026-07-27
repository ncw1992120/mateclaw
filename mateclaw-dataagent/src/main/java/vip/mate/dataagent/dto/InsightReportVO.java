package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 洞察报告视图对象
 */
@Data
public class InsightReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 关联的仪表盘 ID */
    private Long dashboardId;

    /** 报告名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 报告 HTML 内容 */
    private String reportContent;

    /** ECharts option 数据（JSON 格式，供报告页渲染图表） */
    private String echartsOptions;

    /** 状态：draft / published */
    private String status;

    /** 所有者用户 ID */
    private Long ownerId;

    /** 负责人名称 */
    private String ownerName;

    /** 修改人 */
    private String modifier;

    private String createTime;

    private String updateTime;
}
