package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据集字段实体
 * <p>
 * 保存数据集的每个字段定义，包含字段分类（维度/度量）和别名。
 */
@Data
@TableName("dataagent_dataset_field")
public class DatasetFieldEntity {

    @TableId(type = IdType.ASSIGN_ID)
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
