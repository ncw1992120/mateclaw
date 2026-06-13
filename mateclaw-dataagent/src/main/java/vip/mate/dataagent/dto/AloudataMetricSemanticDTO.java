package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Aloudata 指标语义信息
 * <p>
 * 从指标平台同步的指标完整语义，包含指标名称、展示名、业务口径、同义词、可用维度等。
 * 用于直接映射为语义模型，无需用户手动填写。
 */
@Data
public class AloudataMetricSemanticDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 指标 ID */
    private String metricId;

    /** 指标英文名 */
    private String metricName;

    /** 指标展示名（业务别名） */
    private String metricDisplayName;

    /** 指标类型：ATOMIC / DERIVED / COMPOSITE */
    private String type;

    /** 业务口径描述 */
    private String businessCaliber;

    /** 负责人 */
    private String owner;

    /** 业务负责人 */
    private String businessOwner;

    /** 类目 ID */
    private String metricCategoryId;

    /** 类目名称 */
    private String metricCategoryName;

    /** 指标状态：ONLINE / OFFLINE */
    private String status;

    /** 指标单位 */
    private String unit;

    /** 同义词列表 */
    private List<String> synonyms;

    /** 可用维度名称列表 */
    private List<String> availableDimensions;
}
