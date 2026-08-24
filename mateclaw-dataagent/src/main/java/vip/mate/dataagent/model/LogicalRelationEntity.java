package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 逻辑外键关系实体
 * <p>
 * 定义表间的逻辑关联关系，弥补数据库物理外键缺失的问题。
 * 帮助 LLM 理解 JOIN 关系，是多表查询准确率的关键保障。
 */
@Data
@TableName("dataagent_logical_relation")
public class LogicalRelationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 所属工作区 ID */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 创建者用户 ID（资源归属人；历史数据为 NULL，仅工作区管理员可维护） */
    @TableField("owner_id")
    private Long ownerId;

    /** 源表名 */
    private String sourceTableName;

    /** 源字段名 */
    private String sourceColumnName;

    /** 目标表名 */
    private String targetTableName;

    /** 目标字段名 */
    private String targetColumnName;

    /** 关系类型：1:1 / 1:N / N:1 */
    private String relationType;

    /** 业务描述（如"订单关联客户"） */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;

    /**
     * 获取用于 Prompt 的关系描述文本
     */
    public String getPromptInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(sourceTableName).append(".").append(sourceColumnName);
        sb.append(" -> ");
        sb.append(targetTableName).append(".").append(targetColumnName);
        if (relationType != null && !relationType.isBlank()) {
            sb.append(" [").append(relationType).append("]");
        }
        if (description != null && !description.isBlank()) {
            sb.append(" - ").append(description);
        }
        return sb.toString();
    }
}
