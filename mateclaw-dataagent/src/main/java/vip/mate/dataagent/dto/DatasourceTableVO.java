package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 数据源表视图对象
 */
@Data
public class DatasourceTableVO {

    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 表名 */
    private String tableName;

    /** 表注释 */
    private String tableComment;

    /** 表类型 */
    private String tableType;

    /** 估算行数 */
    private Long rowCount;

    /** 估算数据大小（字节） */
    private Long dataSizeBytes;

    /** Schema 名称 */
    private String schemaName;

    /** 引擎/存储类型 */
    private String engine;

    /** 字段数量 */
    private Integer columnCount;

    /** 字段列表 */
    private List<DatasourceColumnVO> columns;

    private String createTime;

    private String updateTime;
}