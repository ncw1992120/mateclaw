package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 逻辑外键关系视图对象
 */
@Data
public class LogicalRelationVO {

    /** 主键 ID */
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

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

    /** 业务描述 */
    private String description;

    /** Prompt 格式的关系描述 */
    private String promptInfo;

    /** 创建时间 */
    private String createTime;

    /** 更新时间 */
    private String updateTime;
}
