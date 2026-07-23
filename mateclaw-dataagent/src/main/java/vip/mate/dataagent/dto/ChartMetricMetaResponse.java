package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 图表「指标查看」元数据解析响应
 * <p>
 * 指标名/口径/维度中文名来自本地语义层元数据表（dataagent_aloudata_metric /
 * dataagent_aloudata_metric_dimension），时间范围/业务限定来自查询入参透传。
 */
@Data
public class ChartMetricMetaResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 指标列表（已解析中文名与口径） */
    private List<MetricItem> metrics = new ArrayList<>();

    /** 分析维度列表（已解析中文名） */
    private List<DimItem> dimensions = new ArrayList<>();

    /** 时间范围（timeConstraint 透传，可读展示） */
    private String timeRange;

    /** 业务限定（filters 透传） */
    private List<String> filters = new ArrayList<>();

    /** 单个指标元数据 */
    @Data
    public static class MetricItem implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 指标英文名（基名，已去除快速计算后缀） */
        private String name;
        /** 指标中文展示名 */
        private String displayName;
        /** 业务口径 */
        private String caliber;
        /** 单位（优先中文单位） */
        private String unit;
        /** 类目名称 */
        private String category;
    }

    /** 单个维度元数据 */
    @Data
    public static class DimItem implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 维度英文名（基名） */
        private String name;
        /** 维度中文展示名 */
        private String displayName;
    }
}
