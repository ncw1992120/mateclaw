package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Aloudata 维度信息
 */
@Data
public class AloudataDimensionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维度 ID */
    private String dimensionId;

    /** 维度名称 */
    private String dimensionName;

    /** 维度显示名称 */
    private String dimensionDisplayName;

    /** 维度类型：COLUMN_BIND（列绑定）/ CUSTOM（自定义） */
    private String type;

    /** 维度描述 */
    private String description;

    /** 数据类型 */
    private String dataType;

    /** 所有者 */
    private String owner;

    /** 业务所有者 */
    private String businessOwner;

    /** 类目 ID */
    private String dimensionCategoryId;

    /** 绑定数据集名 */
    private String datasetName;

    /** 是否时间维度 */
    private Boolean isTimeDimension;

    /** 示例值（逗号分隔） */
    private String exampleValues;
}
