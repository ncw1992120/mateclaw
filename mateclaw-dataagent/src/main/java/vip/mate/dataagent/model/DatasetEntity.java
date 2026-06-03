package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据集实体
 * <p>
 * 数据集是基于数据源表构建的数据模型，保存字段配置和关联关系。
 */
@Data
@TableName("dataagent_dataset")
public class DatasetEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 数据集名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 数据源名称（冗余存储，便于列表展示） */
    private String datasourceName;

    /** 关联的数据源表 ID（逗号分隔，支持多表） */
    private String tableIds;

    /** 关联的数据源表名（逗号分隔，与 tableIds 对应） */
    private String tableNames;

    /** 数据集状态：draft / ready / error */
    private String status;

    /** 行数 */
    private Long rowCount;

    /** 列数 */
    private Integer columnCount;

    /** 所有者 */
    private String owner;

    /** 修改人 */
    private String modifier;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
