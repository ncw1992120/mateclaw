package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 字段级语义模型视图对象
 */
@Data
public class SemanticModelVO {

    /** 主键 ID */
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 表名 */
    private String tableName;

    /** 字段名 */
    private String columnName;

    /** 业务别名 */
    private String businessName;

    /** 业务描述 */
    private String businessDescription;

    /** 同义词 */
    private String synonyms;

    /** 物理数据类型 */
    private String dataType;

    /** 数据库原始注释 */
    private String columnComment;

    /** 示例值 */
    private String exampleValues;

    /** 枚举值 JSON */
    private String enumValues;

    /** 单位 */
    private String unit;

    /** 值域范围 */
    private String valueRange;

    /** 状态：0-停用 / 1-启用 */
    private Integer status;

    /** Prompt 格式的语义信息 */
    private String promptInfo;

    /** 创建时间 */
    private String createTime;

    /** 更新时间 */
    private String updateTime;
}
