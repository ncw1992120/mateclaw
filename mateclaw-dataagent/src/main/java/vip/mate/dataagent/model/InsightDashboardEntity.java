package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 洞察仪表盘实体
 * <p>
 * 存储低代码仪表盘的组件配置 Schema，支持拖拽编辑、组件数据绑定和 AI 解读报告。
 */
@Data
@TableName("dataagent_insight_dashboard")
public class InsightDashboardEntity {

    @TableId(type = IdType.ASSIGN_ID)
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
    @TableField("workspace_id")
    private Long workspaceId;

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
