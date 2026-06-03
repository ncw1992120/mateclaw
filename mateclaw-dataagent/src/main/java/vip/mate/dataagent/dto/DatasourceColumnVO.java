package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 数据源字段视图对象
 */
@Data
public class DatasourceColumnVO {

    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 关联表 ID */
    private Long tableId;

    /** 字段名 */
    private String columnName;

    /** 字段注释 */
    private String columnComment;

    /** 字段数据类型 */
    private String dataType;

    /** 字段长度 */
    private Integer columnSize;

    /** 小数位数 */
    private Integer decimalDigits;

    /** 是否可为空 */
    private Boolean nullable;

    /** 是否为主键 */
    private Boolean primaryKey;

    /** 是否为索引字段 */
    private Boolean indexed;

    /** 默认值 */
    private String defaultValue;

    /** 排序位置 */
    private Integer ordinalPosition;

    /** 外键关联表名 */
    private String foreignKeyTable;

    /** 外键关联字段名 */
    private String foreignKeyColumn;

    private String createTime;

    private String updateTime;
}