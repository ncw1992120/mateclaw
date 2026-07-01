package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Aloudata 维度语义信息
 * <p>
 * 从指标平台同步的维度完整语义，包含维度名称、展示名、描述、同义词等。
 * 用于直接映射为语义模型，无需用户手动填写。
 */
@Data
public class AloudataDimensionSemanticDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维度 ID */
    private String dimensionId;

    /** 维度英文名 */
    private String dimName;

    /** 维度展示名（业务别名） */
    private String dimDisplayName;

    /** 维度数据类型 */
    private String originDataType;

    /** 维度描述 */
    private String dimDescription;

    /** 维度类型：COLUMN_BIND / CUSTOM */
    private String configType;

    /** 维度配置值（列名或自定义表达式） */
    private String configValue;

    /** 绑定的数据集名称 */
    private String datasetName;

    /** 同义词列表 */
    private List<String> synonyms;

    /** 维度状态：ONLINE / OFFLINE */
    private String status;

    /** 维度类目 ID */
    private String categoryId;

    /** 维度类目名称 */
    private String categoryName;
}
