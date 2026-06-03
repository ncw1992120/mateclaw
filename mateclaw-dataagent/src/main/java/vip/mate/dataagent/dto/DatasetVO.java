package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 数据集视图对象
 */
@Data
public class DatasetVO {

    private Long id;

    /** 数据集名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 数据源名称 */
    private String datasourceName;

    /** 关联表 ID 列表 */
    private String tableIds;

    /** 关联表名列表 */
    private String tableNames;

    /** 数据集状态 */
    private String status;

    /** 行数 */
    private Long rowCount;

    /** 列数 */
    private Integer columnCount;

    /** 所有者 */
    private String owner;

    /** 修改人 */
    private String modifier;

    private String createTime;

    private String updateTime;

    /** 字段列表（详情时填充） */
    private List<DatasetFieldVO> fields;
}
