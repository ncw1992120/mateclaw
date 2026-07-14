package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 归因分析响应 DTO
 * <p>
 * 封装 Aloudata 归因分析 API 的返回结果，包括指标校验、多维归因、下钻归因、树归因四种场景。
 */
@Data
public class AttributionAnalysisResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求是否成功 */
    private Boolean success;

    /** 响应码 */
    private String code;

    /** 错误信息 */
    private String errorMsg;

    /** 链路追踪 ID */
    private String traceId;

    /** 归因校验结果（attribution_check 响应） */
    private CheckResult checkResult;

    /** 多维归因结果（attribution_multi_dim / attribution_drilldown 响应） */
    private MultiDimResult multiDimResult;

    /** 指标拆解结果（breakdown 响应），用于树归因 */
    private MetricTreeDef metricTreeDef;

    /** 树归因结果（attribution_tree 响应），key 为节点 ID */
    private Map<String, TreeNodeAttribution> treeResult;

    /**
     * 归因校验结果
     */
    @Data
    public static class CheckResult implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 是否支持归因 */
        private Boolean result;

        /** 错误信息 */
        private String errorMsg;
    }

    /**
     * 多维归因结果
     */
    @Data
    public static class MultiDimResult implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 指标 code */
        private String metric;

        /** 整体变化概要 */
        private AllSummary all;

        /** 各维度归因详情，key 为维度名 */
        private Map<String, DimAttribution> dimensions;
    }

    /**
     * 整体变化概要
     */
    @Data
    public static class AllSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 当前值 */
        private Double currentValue;

        /** 对比值 */
        private Double comparisonValue;

        /** 增长值 */
        private Double growth;

        /** 增长率 */
        private Double growthRate;

        /** 总体贡献率 */
        private Double overallContributionRate;

        /** 相对贡献率 */
        private Double relativeContributionRate;
    }

    /**
     * 维度归因详情
     */
    @Data
    public static class DimAttribution implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 维度值列表 */
        private List<String> dimensionValue;

        /** 当前值列表 */
        private List<Double> currentValue;

        /** 对比值列表 */
        private List<Double> comparisonValue;

        /** 增长值列表 */
        private List<Double> growth;

        /** 增长率列表 */
        private List<Double> growthRate;

        /** 贡献率列表 */
        private List<Double> contributionRate;

        /** 总体贡献率列表 */
        private List<Double> overallContributionRate;

        /** 相对贡献率列表 */
        private List<Double> relativeContributionRate;
    }

    /**
     * 指标树定义（breakdown 接口返回）
     */
    @Data
    public static class MetricTreeDef implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 根节点 ID */
        private String rootNode;

        /** 指标树计算关系，key 为父节点 ID，value 为拆解表达式 */
        private Map<String, String> metricTree;

        /** 节点 ID 与指标 code 或临时指标 ID 的映射 */
        private Map<String, String> metricTreeNodes;

        /** 临时指标定义 */
        private Map<String, Object> metricDefinitions;
    }

    /**
     * 树归因节点归因结果
     */
    @Data
    public static class TreeNodeAttribution implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 当前值 */
        private Double currentValue;

        /** 对比值 */
        private Double comparisonValue;

        /** 增长值 */
        private Double growth;

        /** 增长率 */
        private Double growthRate;

        /** 相对贡献率 */
        private Double relativeContributionRate;

        /** 节点对应的指标名称 */
        private String metricName;
    }
}
