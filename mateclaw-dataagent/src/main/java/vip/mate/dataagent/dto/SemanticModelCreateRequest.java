package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 创建字段级语义模型请求
 */
@Data
public class SemanticModelCreateRequest {

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

    /** 同义词（逗号分隔） */
    private String synonyms;

    /** 物理数据类型 */
    private String dataType;

    /** 数据库原始注释 */
    private String columnComment;

    /** 示例值（逗号分隔） */
    private String exampleValues;

    /** 枚举值 JSON */
    private String enumValues;

    /** 单位 */
    private String unit;

    /** 值域范围 */
    private String valueRange;
}
