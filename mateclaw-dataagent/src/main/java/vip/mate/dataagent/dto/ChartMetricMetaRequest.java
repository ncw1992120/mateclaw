package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图表「指标查看」元数据解析请求
 * <p>
 * 前端从图表所属消息的 {@code aloudata_metrics_query} 工具入参中提取，
 * 后端据此按 metricName / dimName 查本地语义层元数据表，解析出中文指标名、
 * 业务口径、维度中文名等，供图表侧展示。
 */
@Data
public class ChartMetricMetaRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 指标平台数据源 ID（本地系统标识） */
    private Long datasourceId;

    /** 指标名称列表（可能带快速计算后缀，如 sales_amount__sameperiod__yoy__growth） */
    private List<String> metrics;

    /** 维度名称列表（可能含 metric_time__month 等系统时间维度） */
    private List<String> dimensions;

    /** 指标日期范围约束表达式（时间范围） */
    private String timeConstraint;

    /** 全局筛选条件表达式数组（业务限定） */
    private List<String> filters;
}
