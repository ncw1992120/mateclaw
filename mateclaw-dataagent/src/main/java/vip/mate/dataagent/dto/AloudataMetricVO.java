package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Aloudata 指标信息
 */
@Data
public class AloudataMetricVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 指标 ID */
    private String metricId;

    /** 指标名称 */
    private String metricName;

    /** 指标显示名称 */
    private String metricDisplayName;

    /** 指标类型：ATOMIC（原子指标）/ DERIVED（派生指标） */
    private String type;

    /** 业务口径描述 */
    private String businessCaliber;

    /** 所有者 */
    private String owner;

    /** 业务所有者 */
    private String businessOwner;

    /** 类目 ID */
    private String metricCategoryId;

    /** 可用维度列表 */
    private List<String> availableDimensions;
}
