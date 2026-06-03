package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源表元数据实体
 * <p>
 * 动态 Schema 发现后存储表结构信息，包括表名、表注释、行数等。
 */
@Data
@TableName("dataagent_datasource_tables")
public class DatasourceTableEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 表名 */
    private String tableName;

    /** 表注释/说明 */
    private String tableComment;

    /** 表类型：table / view / materialized_view / external */
    private String tableType;

    /** 估算行数 */
    private Long rowCount;

    /** 估算数据大小（字节） */
    private Long dataSizeBytes;

    /** Schema 名称 */
    private String schemaName;

    /** 引擎/存储类型（ClickHouse engine、MongoDB collection type等） */
    private String engine;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}