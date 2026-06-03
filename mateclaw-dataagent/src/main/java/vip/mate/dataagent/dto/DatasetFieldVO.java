package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 数据集字段视图对象
 */
@Data
public class DatasetFieldVO {

    private Long id;

    /** 所属数据集 ID */
    private Long datasetId;

    /** 原始列名 */
    private String columnName;

    /** 字段别名 */
    private String columnAlias;

    /** 字段注释 */
    private String columnComment;

    /** 数据类型 */
    private String dataType;

    /** 字段大小 */
    private Integer columnSize;

    /** 小数位数 */
    private Integer decimalDigits;

    /** 字段分类：dimension / measure */
    private String fieldCategory;

    /** 是否主键 */
    private Boolean primaryKey;

    /** 是否可空 */
    private Boolean nullable;

    /** 默认值 */
    private String defaultValue;

    /** 排序位置 */
    private Integer ordinalPosition;

    /** 来源数据源 ID */
    private Long datasourceId;

    /** 来源表 ID */
    private Long sourceTableId;

    /** 来源表名 */
    private String sourceTableName;

    private String createTime;

    private String updateTime;
}
