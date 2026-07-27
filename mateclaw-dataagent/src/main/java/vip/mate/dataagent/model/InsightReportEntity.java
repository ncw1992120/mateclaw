package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 洞察报告实体
 * <p>
 * 独立存储已发布的报告，报告内容从仪表盘的 reportContent 字段复制而来，
 * 支持报告的发布、查询和删除操作。
 */
@Data
@TableName("dataagent_insight_report")
public class InsightReportEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的仪表盘 ID */
    @TableField("dashboard_id")
    private Long dashboardId;

    /** 所属工作区 ID */
    @TableField("workspace_id")
    private Long workspaceId;

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
    @TableField("owner_id")
    private Long ownerId;

    /** 负责人名称 */
    @TableField("owner_name")
    private String ownerName;

    /** 修改人 */
    private String modifier;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
