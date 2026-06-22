package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 指标按类目分组 DTO
 * <p>
 * 将指标按类目归类，前端可直接按分组渲染，无需自行分组。
 */
@Data
public class MetricCategoryGroupDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 类目 ID */
    private String categoryId;

    /** 类目名称 */
    private String categoryName;

    /** 父级类目 ID */
    private String parentId;

    /** 该类目下的指标数量（含所有子类目） */
    private int metricCount;

    /** 该类目下的指标列表 */
    private List<AloudataMetricSemanticDTO> metrics;

    /** 子类目分组 */
    private List<MetricCategoryGroupDTO> children;
}
